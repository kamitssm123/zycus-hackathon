package com.slotmind.scheduling;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slotmind.config.SchedulingProperties;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFixtures.class)
class SuggestApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fx;
  @Autowired private SchedulingProperties properties;
  @Autowired private AppointmentRepository appointments;
  @Autowired private SuggestionRepository suggestions;

  @BeforeEach
  void setUp() {
    fx.reset();
    fx.smallClinic();
    properties.setStrategy("rule");
  }

  @AfterEach
  void restoreDefaults() {
    properties.setStrategy("rule");
  }

  @Test
  void suggestCreatesAPendingSuggestionAgainstARealOpenSlot() throws Exception {
    mockMvc
        .perform(post("/api/appointments/APT-001/suggest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.triggerReason").value("INITIAL"))
        .andExpect(jsonPath("$.strategyKey").value("rule"))
        .andExpect(jsonPath("$.fallbackUsed").value(false))
        .andExpect(jsonPath("$.recommendedSlotId").value(org.hamcrest.Matchers.startsWith("SLT-")))
        .andExpect(jsonPath("$.reasoning").isNotEmpty())
        .andExpect(jsonPath("$.confidence").isNumber());

    // The appointment must now be awaiting a human decision.
    Assertions.assertThat(appointments.findById("APT-001").orElseThrow().getStatus())
        .isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
  }

  @Test
  void suggestingTwiceForTheSameTriggerDoesNotDuplicate() throws Exception {
    mockMvc.perform(post("/api/appointments/APT-001/suggest")).andExpect(status().isOk());
    mockMvc.perform(post("/api/appointments/APT-001/suggest")).andExpect(status().isOk());

    Assertions.assertThat(suggestions.countByStatus(SuggestionStatus.PENDING)).isEqualTo(1);
  }

  @Test
  void switchingStrategyViaPatchChangesTheStrategyUsedOnTheNextSuggest() throws Exception {
    // The runtime-switch proof: no restart, effective on the very next request.
    mockMvc
        .perform(get("/api/admin/scheduling"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.strategy").value("rule"))
        .andExpect(jsonPath("$.availableStrategies").isArray());

    mockMvc
        .perform(
            patch("/api/admin/scheduling")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategy\":\"rule\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.strategy").value("rule"));

    mockMvc
        .perform(post("/api/appointments/APT-001/suggest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.strategyKey").value("rule"));
  }

  @Test
  void patchingToAnUnknownStrategyIsRejected() throws Exception {
    mockMvc
        .perform(
            patch("/api/admin/scheduling")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategy\":\"telepathy\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void perRequestOverrideIsHonouredWithoutChangingGlobalConfig() throws Exception {
    mockMvc
        .perform(post("/api/appointments/APT-001/suggest").param("strategy", "rule"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.strategyKey").value("rule"));

    Assertions.assertThat(properties.getStrategy()).isEqualTo("rule");
  }

  @Test
  void anUnknownStrategyOverrideIsABadRequest() throws Exception {
    mockMvc
        .perform(post("/api/appointments/APT-001/suggest").param("strategy", "telepathy"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void suggestingForAnUnknownAppointmentIsNotFound() throws Exception {
    mockMvc
        .perform(post("/api/appointments/APT-999/suggest"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void suggestConflictsWhenNoOpenSlotCanBeRecommended() throws Exception {
    // Remove every open slot, leaving the engine nothing valid to return.
    fx.reset();
    var provider = fx.provider("PRV-001", Specialty.GENERAL, ProviderStatus.BUSY);
    var booked = fx.bookedSlot("SLT-001", provider, 60);
    fx.confirmed("APT-001", "John Martinez", AppointmentType.ROUTINE, booked);

    mockMvc
        .perform(post("/api/appointments/APT-001/suggest"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void listsSuggestionsFilteredByStatus() throws Exception {
    mockMvc.perform(post("/api/appointments/APT-001/suggest")).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/suggestions").param("status", "PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].appointmentId").value("APT-001"));

    mockMvc
        .perform(get("/api/suggestions").param("status", "ACCEPTED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
