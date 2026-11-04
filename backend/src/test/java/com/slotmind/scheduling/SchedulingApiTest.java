package com.slotmind.scheduling;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.scheduling.domain.*;
import com.slotmind.support.TestFixtures;
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
class SchedulingApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestFixtures fx;
  @Autowired private SlotRepository slots;

  private TestFixtures.Clinic clinic;

  @BeforeEach
  void setUp() {
    fx.reset();
    clinic = fx.smallClinic();
  }

  private String json(Object o) throws Exception {
    return objectMapper.writeValueAsString(o);
  }

  @Test
  void booksAnAppointmentOntoAnOpenSlot() throws Exception {
    mockMvc
        .perform(
            post("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientName":"Aisha Patel","type":"URGENT",
                     "providerId":"PRV-004","slotId":"SLT-005"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.patientName").value("Aisha Patel"))
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("APT-")));

    // the slot must no longer be bookable
    org.assertj.core.api.Assertions.assertThat(slots.findById("SLT-005").orElseThrow().getStatus())
        .isEqualTo(SlotStatus.BOOKED);
  }

  @Test
  void rejectsBlankPatientNameWithFieldLevelDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientName":"  ","type":"ROUTINE",
                     "providerId":"PRV-004","slotId":"SLT-005"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0].field").value("patientName"));
  }

  @Test
  void bookingAnAlreadyBookedSlotReturnsStructuredConflict() throws Exception {
    mockMvc
        .perform(
            post("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientName":"Robert Kim","type":"ROUTINE",
                     "providerId":"PRV-001","slotId":"SLT-001"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.requestId").exists());
  }

  @Test
  void returnsStructuredNotFoundForAnUnknownAppointment() throws Exception {
    mockMvc
        .perform(get("/api/appointments/APT-999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.requestId").exists());
  }

  @Test
  void listsAppointmentsFilteredByStatus() throws Exception {
    mockMvc
        .perform(get("/api/appointments").param("status", "CONFIRMED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value("APT-001"));

    mockMvc
        .perform(get("/api/appointments").param("status", "RESCHEDULED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void listsProvidersAndSlots() throws Exception {
    mockMvc
        .perform(get("/api/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].specialty").value("GENERAL"));

    mockMvc
        .perform(get("/api/slots").param("status", "OPEN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void patchingProviderStatusToOfflineSucceedsAndReturnsImmediately() throws Exception {
    mockMvc
        .perform(
            patch("/api/providers/PRV-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"OFFLINE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OFFLINE"));
  }

  @Test
  void patchingSlotStatusToCancelledSucceeds() throws Exception {
    mockMvc
        .perform(
            patch("/api/slots/SLT-005/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void rejectsAnInvalidStatusEnumValue() throws Exception {
    mockMvc
        .perform(
            patch("/api/providers/PRV-001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ON_HOLIDAY\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.requestId").exists());
  }

  @Test
  void rejectsAnInvalidStatusFilterOnTheListEndpoint() throws Exception {
    mockMvc
        .perform(get("/api/appointments").param("status", "NOPE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0].field").value("status"));
  }

  @Test
  void patchingAnUnknownProviderIsNotFound() throws Exception {
    mockMvc
        .perform(
            patch("/api/providers/PRV-999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"OFFLINE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
