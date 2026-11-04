package com.slotmind.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slotmind.scheduling.domain.*;
import com.slotmind.support.TestFixtures;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The headline behaviour: nobody clicks anything.
 *
 * <p>Deliberately NOT @Transactional — an AFTER_COMMIT listener never fires inside a rolled-back
 * test transaction, so the whole thing would silently pass by never running the agent at all.
 * Isolation comes from fx.reset() instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFixtures.class)
class AgenticLoopApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fx;
  @Autowired private SuggestionRepository suggestions;
  @Autowired private AppointmentRepository appointments;

  @BeforeEach
  void setUp() {
    // fx.reset() drains in-flight agent work before wiping — see TestFixtures.reset().
    fx.reset();
    fx.smallClinic();
  }

  @AfterEach
  void drainBeforeNextClass() {
    fx.drainAsyncWork();
  }

  private String body(String status) {
    return "{\"status\":\"" + status + "\"}";
  }

  @Test
  void takingAProviderOfflineCreatesSuggestionsForEveryAffectedAppointment() throws Exception {
    mockMvc
        .perform(
            patch("/api/providers/PRV-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("OFFLINE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OFFLINE"));

    // Asynchronous: the response above returned before any of this existed.
    TestFixtures.awaitUntil(
        "two PENDING suggestions from the provider-offline trigger",
        () -> suggestions.countByStatus(SuggestionStatus.PENDING) == 2);

    Assertions.assertThat(suggestions.findByStatusOrderByCreatedAtDesc(SuggestionStatus.PENDING))
        .hasSize(2)
        .allSatisfy(
            s ->
                Assertions.assertThat(s.getTriggerReason())
                    .isEqualTo(TriggerReason.PROVIDER_OFFLINE))
        .allSatisfy(s -> Assertions.assertThat(s.getReasoning()).isNotBlank())
        .extracting(RescheduleSuggestion::getAppointmentId)
        .containsExactlyInAnyOrder("APT-001", "APT-002");

    Assertions.assertThat(
            appointments.findByStatusOrderByIdAsc(AppointmentStatus.RESCHEDULE_PENDING))
        .hasSize(2);
  }

  @Test
  void theAgentStopsAtPendingAndNeverBooksAnything() throws Exception {
    mockMvc
        .perform(
            patch("/api/providers/PRV-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("OFFLINE")))
        .andExpect(status().isOk());

    TestFixtures.awaitUntil(
        "suggestions created", () -> suggestions.countByStatus(SuggestionStatus.PENDING) == 2);

    // The human checkpoint: nothing is ACCEPTED, and no appointment has moved.
    Assertions.assertThat(suggestions.countByStatus(SuggestionStatus.ACCEPTED)).isZero();
    Assertions.assertThat(appointments.findById("APT-001").orElseThrow().getSlotId())
        .isEqualTo("SLT-001");
    Assertions.assertThat(appointments.findByStatusOrderByIdAsc(AppointmentStatus.RESCHEDULED))
        .isEmpty();
  }

  @Test
  void aRepeatedTriggerForTheSameAppointmentAndReasonIsSkipped() throws Exception {
    mockMvc
        .perform(
            patch("/api/providers/PRV-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("OFFLINE")))
        .andExpect(status().isOk());

    TestFixtures.awaitUntil(
        "first round of suggestions",
        () -> suggestions.countByStatus(SuggestionStatus.PENDING) == 2);

    // Put one appointment back to CONFIRMED so the resolver finds it again on the next trigger.
    // Without this the resolver simply returns nothing and the dedupe guard is never exercised.
    Appointment reopened = appointments.findById("APT-001").orElseThrow();
    reopened.transitionTo(AppointmentStatus.CONFIRMED);
    appointments.save(reopened);

    mockMvc
        .perform(
            patch("/api/providers/PRV-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("AVAILABLE")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            patch("/api/providers/PRV-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("OFFLINE")))
        .andExpect(status().isOk());

    TestFixtures.settle();

    // APT-001 already has a PENDING PROVIDER_OFFLINE suggestion, so the guard skips it.
    Assertions.assertThat(suggestions.countByStatus(SuggestionStatus.PENDING)).isEqualTo(2);
    Assertions.assertThat(
            suggestions.findByAppointmentIdOrderByCreatedAtDesc("APT-001").stream()
                .filter(x -> x.getTriggerReason() == TriggerReason.PROVIDER_OFFLINE)
                .count())
        .isEqualTo(1);
  }

  @Test
  void aFanOutOffersDistinctSlotsRatherThanTheSameOneTwice() throws Exception {
    // Two patients offered the same slot means one coordinator makes a phone call that ends in a
    // 409. The accept transaction would keep the data correct; the experience would still be bad.
    mockMvc
        .perform(
            patch("/api/providers/PRV-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("OFFLINE")))
        .andExpect(status().isOk());

    TestFixtures.awaitUntil(
        "two PENDING suggestions", () -> suggestions.countByStatus(SuggestionStatus.PENDING) == 2);

    Assertions.assertThat(suggestions.findByStatusOrderByCreatedAtDesc(SuggestionStatus.PENDING))
        .extracting(RescheduleSuggestion::getRecommendedSlotId)
        .doesNotHaveDuplicates();
  }

  @Test
  void aPatientMovedOntoAProviderIsRescheduledAgainWhenThatProviderGoesOffline() throws Exception {
    // The full real-world sequence: Chen goes offline, John is moved to Verma, then Verma goes
    // offline too. John must not be silently stranded on a booked slot with an absent doctor.
    mockMvc
        .perform(
            patch("/api/providers/PRV-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("OFFLINE")))
        .andExpect(status().isOk());
    TestFixtures.awaitUntil(
        "first round", () -> suggestions.countByStatus(SuggestionStatus.PENDING) == 2);

    String forJohn = suggestions.findByAppointmentIdOrderByCreatedAtDesc("APT-001").get(0).getId();
    mockMvc
        .perform(
            patch("/api/suggestions/" + forJohn)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPTED")))
        .andExpect(status().isOk());

    Appointment moved = appointments.findById("APT-001").orElseThrow();
    Assertions.assertThat(moved.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
    Assertions.assertThat(moved.getProviderId()).isEqualTo("PRV-004");

    // A third doctor with a free slot, so there is somewhere left to move him to. Without this
    // the engine correctly finds no candidates and the test would fail for an unrelated reason.
    var thirdDoctor = fx.provider("PRV-009", Specialty.GENERAL, ProviderStatus.AVAILABLE);
    fx.openSlot("SLT-099", thirdDoctor, 300);

    // Now the provider he was moved TO goes offline.
    mockMvc
        .perform(
            patch("/api/providers/PRV-004/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("OFFLINE")))
        .andExpect(status().isOk());

    TestFixtures.awaitUntil(
        "John to be picked up again",
        () ->
            appointments.findById("APT-001").orElseThrow().getStatus()
                == AppointmentStatus.RESCHEDULE_PENDING);

    Assertions.assertThat(suggestions.findByAppointmentIdOrderByCreatedAtDesc("APT-001"))
        .anySatisfy(
            s -> {
              Assertions.assertThat(s.isPending()).isTrue();
              Assertions.assertThat(s.getRecommendedSlotId()).isEqualTo("SLT-099");
            });
  }

  @Test
  void cancellingASlotCreatesOneSuggestionWithTheSlotCancelledTrigger() throws Exception {
    mockMvc
        .perform(
            patch("/api/slots/SLT-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("CANCELLED")))
        .andExpect(status().isOk());

    TestFixtures.awaitUntil(
        "one PENDING suggestion from the slot-cancelled trigger",
        () -> suggestions.countByStatus(SuggestionStatus.PENDING) == 1);

    Assertions.assertThat(suggestions.findByStatusOrderByCreatedAtDesc(SuggestionStatus.PENDING))
        .singleElement()
        .satisfies(
            s -> {
              Assertions.assertThat(s.getTriggerReason()).isEqualTo(TriggerReason.SLOT_CANCELLED);
              Assertions.assertThat(s.getAppointmentId()).isEqualTo("APT-001");
            });
  }

  @Test
  void settingAProviderBusyDoesNotTriggerTheLoop() throws Exception {
    // Only OFFLINE is a disruption. BUSY is normal operation.
    mockMvc
        .perform(
            patch("/api/providers/PRV-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("BUSY")))
        .andExpect(status().isOk());

    TestFixtures.settle();
    Assertions.assertThat(suggestions.countByStatus(SuggestionStatus.PENDING)).isZero();
  }
}
