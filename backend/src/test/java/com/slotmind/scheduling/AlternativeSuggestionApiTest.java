package com.slotmind.scheduling;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** "That one doesn't suit — what else?" in one call. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFixtures.class)
class AlternativeSuggestionApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fx;
  @Autowired private AppointmentRepository appointments;
  @Autowired private SuggestionRepository suggestions;

  private TestFixtures.Clinic clinic;

  @BeforeEach
  void setUp() {
    fx.reset();
    clinic = fx.smallClinic();
  }

  /** Puts APT-001 into RESCHEDULE_PENDING with a PENDING suggestion for the given slot. */
  private String pendingFor(Slot target) {
    Appointment appointment = clinic.appointment1();
    if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
      appointment.transitionTo(AppointmentStatus.RESCHEDULE_PENDING);
      appointments.save(appointment);
    }
    return fx.pending(appointment, target, TriggerReason.PROVIDER_OFFLINE).getId();
  }

  @Test
  void proposesADifferentSlotThanTheOneJustRefused() throws Exception {
    String first = pendingFor(clinic.openSlot1()); // SLT-005

    mockMvc
        .perform(post("/api/suggestions/" + first + "/alternative"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rejected.suggestionStatus").value("REJECTED"))
        .andExpect(jsonPath("$.alternative").exists())
        // The whole point: a genuinely different time, not the same answer with a new id.
        .andExpect(jsonPath("$.alternative.recommendedSlotId").value("SLT-007"))
        .andExpect(jsonPath("$.alternative.status").value("PENDING"));
  }

  @Test
  void keepsTheOriginalTriggerReasonSoTheBadgeAndPromptStayTruthful() throws Exception {
    String first = pendingFor(clinic.openSlot1());

    mockMvc
        .perform(post("/api/suggestions/" + first + "/alternative"))
        .andExpect(status().isOk())
        // Not INITIAL: this patient is still being moved because their provider went offline.
        .andExpect(jsonPath("$.alternative.triggerReason").value("PROVIDER_OFFLINE"));
  }

  @Test
  void leavesTheAppointmentAwaitingADecisionWhenAnAlternativeExists() throws Exception {
    String first = pendingFor(clinic.openSlot1());

    mockMvc.perform(post("/api/suggestions/" + first + "/alternative")).andExpect(status().isOk());

    Assertions.assertThat(appointments.findById("APT-001").orElseThrow().getStatus())
        .isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
    Assertions.assertThat(suggestions.countByStatus(SuggestionStatus.PENDING)).isEqualTo(1);
  }

  @Test
  void returnsANullAlternativeAndConfirmsTheAppointmentWhenNothingIsLeft() throws Exception {
    // Refuse both open slots; there is then nothing else to offer.
    String first = pendingFor(clinic.openSlot1());
    mockMvc.perform(post("/api/suggestions/" + first + "/alternative")).andExpect(status().isOk());

    String second =
        suggestions.findByStatusOrderByCreatedAtDesc(SuggestionStatus.PENDING).get(0).getId();

    mockMvc
        .perform(post("/api/suggestions/" + second + "/alternative"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rejected.suggestionStatus").value("REJECTED"))
        // The rejection still succeeded — this is not an error.
        .andExpect(jsonPath("$.alternative").doesNotExist());

    // The patient keeps their original appointment rather than being stranded mid-reschedule.
    Assertions.assertThat(appointments.findById("APT-001").orElseThrow().getStatus())
        .isEqualTo(AppointmentStatus.CONFIRMED);
    Assertions.assertThat(appointments.findById("APT-001").orElseThrow().getSlotId())
        .isEqualTo("SLT-001");
  }

  @Test
  void aSlotRefusedOnceIsNeverOfferedAgain() throws Exception {
    String first = pendingFor(clinic.openSlot1());
    mockMvc.perform(post("/api/suggestions/" + first + "/alternative")).andExpect(status().isOk());
    String second =
        suggestions.findByStatusOrderByCreatedAtDesc(SuggestionStatus.PENDING).get(0).getId();
    mockMvc.perform(post("/api/suggestions/" + second + "/alternative")).andExpect(status().isOk());

    Assertions.assertThat(
            suggestions.findByAppointmentIdOrderByCreatedAtDesc("APT-001").stream()
                .map(RescheduleSuggestion::getRecommendedSlotId)
                .toList())
        .doesNotHaveDuplicates();
  }

  @Test
  void askingForAnAlternativeToAnAlreadyDecidedSuggestionIsAConflict() throws Exception {
    String first = pendingFor(clinic.openSlot1());
    mockMvc
        .perform(
            patch("/api/suggestions/" + first)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/suggestions/" + first + "/alternative"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void askingForAnAlternativeToAnUnknownSuggestionIsNotFound() throws Exception {
    mockMvc
        .perform(post("/api/suggestions/SGT-999/alternative"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
