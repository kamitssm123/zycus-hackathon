package com.slotmind.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slotmind.common.exception.ConflictException;
import com.slotmind.scheduling.domain.*;
import com.slotmind.scheduling.service.SuggestionDecisionService;
import com.slotmind.support.PostgresIT;
import com.slotmind.support.TestFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AGT-3: two coordinators accept two different suggestions that point at the SAME open slot.
 * Exactly one must win.
 *
 * <p>The test asserts an INVARIANT that holds under every possible interleaving — exactly one
 * success, exactly one conflict, the slot booked once — rather than "thread 1 wins", which would be
 * flaky by construction. @RepeatedTest widens the window rather than chasing a specific ordering.
 *
 * <p>Deliberately NOT @Transactional. That annotation would break this three independent ways:
 * fixtures would be uncommitted and invisible to the worker threads; the test thread's locks would
 * deadlock against them; and teardown would roll back only the loser, since the winner committed on
 * a different connection.
 *
 * <p>Runs under {@code mvn verify} (failsafe) only — H2 has neither row-level locking semantics nor
 * partial unique indexes, so this cannot live in the fast suite.
 */
class AcceptSuggestionConcurrencyIT extends PostgresIT {

  @Autowired private SuggestionDecisionService decisions;
  @Autowired private TestFixtures fx;
  @Autowired private TransactionTemplate tx;
  @Autowired private SlotRepository slots;
  @Autowired private AppointmentRepository appointments;
  @Autowired private SuggestionRepository suggestions;

  private static final String CONTESTED = "SLT-900";

  @BeforeEach
  void seed() {
    tx.executeWithoutResult(s -> fx.reset());
  }

  @AfterEach
  void wipe() {
    tx.executeWithoutResult(s -> fx.reset());
  }

  /**
   * Two appointments, two PENDING suggestions, one contested open slot. Committed before return.
   */
  private List<String> seedContest() {
    return tx.execute(
        s -> {
          Provider chen = fx.provider("PRV-001", Specialty.GENERAL, ProviderStatus.OFFLINE);
          Provider verma = fx.provider("PRV-004", Specialty.GENERAL, ProviderStatus.AVAILABLE);
          Slot held1 = fx.bookedSlot("SLT-001", chen, 60);
          Slot held2 = fx.bookedSlot("SLT-002", chen, 120);
          Slot contested = fx.openSlot(CONTESTED, verma, 150);

          Appointment a1 =
              fx.reschedulePending("APT-001", "John Martinez", AppointmentType.ROUTINE, held1);
          Appointment a2 =
              fx.reschedulePending("APT-002", "Emily Watson", AppointmentType.FOLLOW_UP, held2);

          return List.of(
              fx.pending(a1, contested, TriggerReason.PROVIDER_OFFLINE).getId(),
              fx.pending(a2, contested, TriggerReason.PROVIDER_OFFLINE).getId());
        });
  }

  private record Outcome(boolean won, String message) {}

  @RepeatedTest(10)
  void exactlyOneOfTwoConcurrentAcceptsWinsTheContestedSlot() throws Exception {
    List<String> suggestionIds = seedContest();
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    try {
      List<Callable<Outcome>> attempts = new ArrayList<>();
      for (String suggestionId : suggestionIds) {
        attempts.add(
            () -> {
              barrier.await(5, TimeUnit.SECONDS); // true rendezvous: neither proceeds alone
              try {
                decisions.accept(suggestionId); // opens its OWN transaction on its OWN connection
                return new Outcome(true, null);
              } catch (ConflictException | DataIntegrityViolationException e) {
                return new Outcome(false, e.getMessage());
              }
            });
      }

      List<Outcome> outcomes = new ArrayList<>();
      for (Future<Outcome> future : pool.invokeAll(attempts)) {
        outcomes.add(future.get(20, TimeUnit.SECONDS));
      }

      // --- the invariant ---
      assertThat(outcomes).filteredOn(Outcome::won).hasSize(1);
      List<Outcome> losers = outcomes.stream().filter(o -> !o.won()).toList();
      assertThat(losers).hasSize(1);
      // Guard against passing for the wrong reason: the 409 must come from the slot guard,
      // not from an unrelated state check.
      assertThat(losers.get(0).message()).containsAnyOf(CONTESTED, "just booked by someone else");

      // --- and the database agrees ---
      tx.executeWithoutResult(
          s -> {
            assertThat(slots.findById(CONTESTED).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);
            assertThat(
                    appointments.countBySlotIdAndStatusNot(CONTESTED, AppointmentStatus.COMPLETED))
                .isEqualTo(1);
            assertThat(
                    appointments.findAllByOrderByIdAsc().stream()
                        .filter(a -> a.getStatus() == AppointmentStatus.RESCHEDULED)
                        .count())
                .isEqualTo(1);
            assertThat(
                    suggestions.findAllByOrderByCreatedAtDesc().stream()
                        .filter(x -> x.getStatus() == SuggestionStatus.ACCEPTED)
                        .count())
                .isEqualTo(1);
          });
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void theLosingAcceptLeavesItsAppointmentAndOriginalSlotUntouched() {
    List<String> ids = seedContest();

    decisions.accept(ids.get(0));
    assertThatThrownBy(() -> decisions.accept(ids.get(1))).isInstanceOf(ConflictException.class);

    tx.executeWithoutResult(
        s -> {
          Appointment loser = appointments.findById("APT-002").orElseThrow();
          assertThat(loser.getSlotId()).isEqualTo("SLT-002");
          assertThat(loser.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
          assertThat(slots.findById("SLT-002").orElseThrow().getStatus())
              .isEqualTo(SlotStatus.BOOKED);
        });
  }

  @Test
  void thePartialUniqueIndexRejectsADirectDoubleBooking() {
    // The only test that proves the Postgres-only backstop exists at all. H2 cannot express a
    // partial unique index, so without this the backstop is untested infrastructure.
    seedContest();

    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      Appointment a = appointments.findById("APT-002").orElseThrow();
                      a.setSlotId("SLT-001"); // already held by APT-001
                      appointments.saveAndFlush(a);
                    }))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
