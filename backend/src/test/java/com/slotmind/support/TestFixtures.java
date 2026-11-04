package com.slotmind.support;

import com.slotmind.scheduling.domain.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Shared fixture builder. Flyway is disabled in the test profile (schema comes from Hibernate
 * create-drop), so the demo seed never runs in tests and every test builds what it needs here.
 *
 * <p>Type-safe on purpose: an entity rename becomes a compile error rather than a runtime surprise.
 * {@link #reset()} in {@code @BeforeEach} gives isolation WITHOUT {@code @Transactional} — which
 * the async loop tests cannot use, because {@code @TransactionalEventListener(AFTER_COMMIT)} never
 * fires inside a rolled-back test transaction.
 */
@TestComponent
@RequiredArgsConstructor
public class TestFixtures {

  /**
   * Base instant for fixture slots, anchored to the next whole hour.
   *
   * <p>It MUST be relative to the real clock, not a fixed literal: SchedulingEngine filters
   * candidate slots to those still in the future, so fixtures pinned to a past timestamp silently
   * disappear from the candidate set and the suite starts passing or failing depending on what time
   * of day it runs. (That is exactly what happened with a hard-coded 09:00Z.)
   *
   * <p>Determinism where it actually matters — strategy scoring and ordering — lives in
   * RuleBasedStrategyTest, which supplies its own fixed clock through SchedulingContext.
   */
  public static final Instant BASE =
      Instant.now().truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS);

  private final ProviderRepository providers;
  private final SlotRepository slots;
  private final AppointmentRepository appointments;
  private final SuggestionRepository suggestions;

  @Autowired(required = false)
  @Qualifier("rescheduleExecutor")
  private Executor rescheduleExecutor;

  /**
   * Wipes everything, but ONLY after in-flight agent work has finished.
   *
   * <p>Without the drain this suite is quietly flaky: any test that PATCHes a provider OFFLINE
   * spawns async work, and because {@code @TransactionalEventListener(AFTER_COMMIT)} submits the
   * task after the HTTP response returns, the next test's reset can delete rows the agent is about
   * to read — surfacing as "Appointment not found" in one test and a timeout in another.
   *
   * <p>Not {@code @Transactional}: deleteAllInBatch is already transactional per call, and wrapping
   * the drain in a transaction would hold locks while we wait.
   */
  public void reset() {
    drainAsyncWork();
    suggestions.deleteAllInBatch();
    appointments.deleteAllInBatch();
    slots.deleteAllInBatch();
    providers.deleteAllInBatch();
  }

  /** Waits for the agent executor to be idle twice in a row, to catch not-yet-submitted tasks. */
  public void drainAsyncWork() {
    if (!(rescheduleExecutor instanceof ThreadPoolTaskExecutor pool)) {
      return;
    }
    int consecutiveIdle = 0;
    long deadline = System.currentTimeMillis() + 5_000;
    while (System.currentTimeMillis() < deadline && consecutiveIdle < 3) {
      boolean idle =
          pool.getActiveCount() == 0 && pool.getThreadPoolExecutor().getQueue().isEmpty();
      consecutiveIdle = idle ? consecutiveIdle + 1 : 0;
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  public Provider provider(String id, Specialty specialty, ProviderStatus status) {
    return providers.save(
        Provider.builder().id(id).name("Dr. " + id).specialty(specialty).status(status).build());
  }

  public Provider provider(String id) {
    return provider(id, Specialty.GENERAL, ProviderStatus.AVAILABLE);
  }

  public Slot slot(String id, Provider provider, int minutesFromBase, SlotStatus status) {
    return slots.save(
        Slot.builder()
            .id(id)
            .providerId(provider.getId())
            .startTime(BASE.plus(minutesFromBase, ChronoUnit.MINUTES))
            .durationMinutes(30)
            .status(status)
            .build());
  }

  public Slot openSlot(String id, Provider provider, int minutesFromBase) {
    return slot(id, provider, minutesFromBase, SlotStatus.OPEN);
  }

  public Slot bookedSlot(String id, Provider provider, int minutesFromBase) {
    return slot(id, provider, minutesFromBase, SlotStatus.BOOKED);
  }

  public Appointment confirmed(String id, String patient, AppointmentType type, Slot slot) {
    return appointments.save(
        Appointment.builder()
            .id(id)
            .patientName(patient)
            .type(type)
            .providerId(slot.getProviderId())
            .slotId(slot.getId())
            .status(AppointmentStatus.CONFIRMED)
            .build());
  }

  public Appointment reschedulePending(String id, String patient, AppointmentType type, Slot slot) {
    Appointment appointment = confirmed(id, patient, type, slot);
    appointment.transitionTo(AppointmentStatus.RESCHEDULE_PENDING);
    return appointments.save(appointment);
  }

  public RescheduleSuggestion pending(
      Appointment appointment, Slot recommended, TriggerReason reason) {
    return suggestions.save(
        RescheduleSuggestion.builder()
            .id("SGT-" + appointment.getId().substring(4))
            .appointmentId(appointment.getId())
            .recommendedSlotId(recommended.getId())
            .confidence(0.9)
            .reasoning("Test suggestion")
            .status(SuggestionStatus.PENDING)
            .triggerReason(reason)
            .strategyKey("rule")
            .build());
  }

  /** The 80% case: one busy GP, one free GP, two booked appointments and spare open slots. */
  public Clinic smallClinic() {
    Provider chen = provider("PRV-001", Specialty.GENERAL, ProviderStatus.BUSY);
    Provider verma = provider("PRV-004", Specialty.GENERAL, ProviderStatus.AVAILABLE);
    Slot booked1 = bookedSlot("SLT-001", chen, 60);
    Slot booked2 = bookedSlot("SLT-002", chen, 120);
    Slot open1 = openSlot("SLT-005", verma, 150);
    Slot open2 = openSlot("SLT-007", verma, 210);
    Appointment apt1 = confirmed("APT-001", "John Martinez", AppointmentType.ROUTINE, booked1);
    Appointment apt2 = confirmed("APT-002", "Emily Watson", AppointmentType.FOLLOW_UP, booked2);
    return new Clinic(chen, verma, booked1, booked2, open1, open2, apt1, apt2);
  }

  public record Clinic(
      Provider busyProvider,
      Provider freeProvider,
      Slot bookedSlot1,
      Slot bookedSlot2,
      Slot openSlot1,
      Slot openSlot2,
      Appointment appointment1,
      Appointment appointment2) {}

  /**
   * Bounded poll for asynchronously-produced state. Used instead of sleeping so the async loop
   * tests stay fast when things work and still fail loudly when they do not.
   */
  public static void awaitUntil(String what, java.util.function.BooleanSupplier condition) {
    long deadline = System.currentTimeMillis() + 5_000;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) return;
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    throw new AssertionError("Timed out after 5s waiting for: " + what);
  }

  /**
   * Brief pause used only when asserting that something did NOT happen — otherwise the assertion
   * can pass simply because the async work has not started yet.
   */
  public static void settle() {
    try {
      Thread.sleep(600);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
