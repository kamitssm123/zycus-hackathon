package com.slotmind.scheduling;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slotmind.scheduling.domain.*;
import com.slotmind.support.TestFixtures;
import org.assertj.core.api.Assertions;
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
 * Accept/reject over HTTP on H2. Proves the contract and the 409 SHAPE; it cannot produce a real
 * race — single-threaded MockMvc on H2 has no contention, and H2 has no partial unique index. That
 * is AcceptSuggestionConcurrencyIT's job.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFixtures.class)
class SuggestionDecisionApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fx;
  @Autowired private AppointmentRepository appointments;
  @Autowired private SlotRepository slots;
  @Autowired private SuggestionRepository suggestions;

  private TestFixtures.Clinic clinic;

  @BeforeEach
  void setUp() {
    fx.reset();
    clinic = fx.smallClinic();
  }

  private String body(String status) {
    return "{\"status\":\"" + status + "\"}";
  }

  private String pendingSuggestionFor(Appointment appointment, Slot target) {
    appointment.transitionTo(AppointmentStatus.RESCHEDULE_PENDING);
    appointments.save(appointment);
    return fx.pending(appointment, target, TriggerReason.PROVIDER_OFFLINE).getId();
  }

  @Test
  void acceptingMovesTheAppointmentAndSwapsBothSlotsAtomically() throws Exception {
    String id = pendingSuggestionFor(clinic.appointment1(), clinic.openSlot1());

    mockMvc
        .perform(
            patch("/api/suggestions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPTED")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suggestionStatus").value("ACCEPTED"))
        .andExpect(jsonPath("$.appointmentStatus").value("RESCHEDULED"))
        .andExpect(jsonPath("$.slotId").value("SLT-005"))
        .andExpect(jsonPath("$.releasedSlotId").value("SLT-001"));

    Assertions.assertThat(slots.findById("SLT-001").orElseThrow().getStatus())
        .isEqualTo(SlotStatus.OPEN);
    Assertions.assertThat(slots.findById("SLT-005").orElseThrow().getStatus())
        .isEqualTo(SlotStatus.BOOKED);
    Assertions.assertThat(appointments.findById("APT-001").orElseThrow().getSlotId())
        .isEqualTo("SLT-005");
  }

  @Test
  void acceptingTwiceReturnsAStructuredConflict() throws Exception {
    String id = pendingSuggestionFor(clinic.appointment1(), clinic.openSlot1());

    mockMvc
        .perform(
            patch("/api/suggestions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPTED")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/suggestions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPTED")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(
            jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already ACCEPTED")))
        .andExpect(jsonPath("$.requestId").exists());
  }

  @Test
  void acceptingASuggestionForAnAlreadyTakenSlotIsAConflictNamingTheSlot() throws Exception {
    // Two appointments, two suggestions, one target slot — the sequential version of the race.
    String first = pendingSuggestionFor(clinic.appointment1(), clinic.openSlot1());
    String second = pendingSuggestionFor(clinic.appointment2(), clinic.openSlot1());

    mockMvc
        .perform(
            patch("/api/suggestions/" + first)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPTED")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/suggestions/" + second)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPTED")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SLT-005")));

    // The loser's appointment is untouched.
    Assertions.assertThat(appointments.findById("APT-002").orElseThrow().getSlotId())
        .isEqualTo("SLT-002");
    Assertions.assertThat(appointments.findById("APT-002").orElseThrow().getStatus())
        .isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
  }

  @Test
  void rejectingReturnsTheAppointmentToConfirmed() throws Exception {
    String id = pendingSuggestionFor(clinic.appointment1(), clinic.openSlot1());

    mockMvc
        .perform(
            patch("/api/suggestions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("REJECTED")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suggestionStatus").value("REJECTED"))
        .andExpect(jsonPath("$.appointmentStatus").value("CONFIRMED"))
        .andExpect(jsonPath("$.slotId").value("SLT-001"));

    Assertions.assertThat(slots.findById("SLT-005").orElseThrow().getStatus())
        .isEqualTo(SlotStatus.OPEN);
  }

  @Test
  void acceptingRejectsSiblingSuggestionsForTheSameAppointment() throws Exception {
    Appointment appointment = clinic.appointment1();
    appointment.transitionTo(AppointmentStatus.RESCHEDULE_PENDING);
    appointments.save(appointment);
    String keep =
        fx.pending(appointment, clinic.openSlot1(), TriggerReason.PROVIDER_OFFLINE).getId();
    RescheduleSuggestion sibling =
        suggestions.save(
            RescheduleSuggestion.builder()
                .id("SGT-900")
                .appointmentId(appointment.getId())
                .recommendedSlotId(clinic.openSlot2().getId())
                .confidence(0.6)
                .reasoning("alternative")
                .status(SuggestionStatus.PENDING)
                .triggerReason(TriggerReason.INITIAL)
                .build());

    mockMvc
        .perform(
            patch("/api/suggestions/" + keep)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPTED")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.siblingsRejected").value(1));

    Assertions.assertThat(suggestions.findById(sibling.getId()).orElseThrow().getStatus())
        .isEqualTo(SuggestionStatus.REJECTED);
  }

  @Test
  void patchingToPendingIsABadRequest() throws Exception {
    String id = pendingSuggestionFor(clinic.appointment1(), clinic.openSlot1());

    mockMvc
        .perform(
            patch("/api/suggestions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("PENDING")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void patchingAnUnknownSuggestionIsNotFound() throws Exception {
    mockMvc
        .perform(
            patch("/api/suggestions/SGT-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPTED")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
