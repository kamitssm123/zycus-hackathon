package com.slotmind.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slotmind.config.SchedulingProperties;
import com.slotmind.llm.LlmClient;
import com.slotmind.llm.LlmUnavailableException;
import com.slotmind.support.TestFixtures;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end AI path with a scripted client. The suite NEVER reaches the real gateway — a test that
 * depends on a QC endpoint and conference wifi is not a test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestFixtures.class, AiStrategyApiTest.ScriptedLlm.class})
class AiStrategyApiTest {

  /** Swappable script so each test decides what the "model" says. */
  static final AtomicReference<String> SCRIPT = new AtomicReference<>("{\"recommendations\":[]}");

  static final AtomicReference<RuntimeException> FAILURE = new AtomicReference<>(null);

  @TestConfiguration
  static class ScriptedLlm {
    @Bean
    @Primary
    LlmClient scriptedLlmClient() {
      return (systemPrompt, userPrompt) -> {
        if (FAILURE.get() != null) throw FAILURE.get();
        return SCRIPT.get();
      };
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private TestFixtures fx;
  @Autowired private SchedulingProperties properties;

  @BeforeEach
  void setUp() {
    fx.reset();
    fx.smallClinic();
    properties.setStrategy("ai");
    FAILURE.set(null);
  }

  @AfterEach
  void restore() {
    properties.setStrategy("rule");
    FAILURE.set(null);
  }

  @Test
  void theAiRecommendationIsUsedWhenItNamesARealOpenSlot() throws Exception {
    SCRIPT.set(
        """
        {"recommendations":[{"slotId":"SLT-005","alternateProviderId":"PRV-004",
          "confidence":0.88,"reasoning":"Dr. Verma has an opening at 1:30 PM today."}]}
        """);

    mockMvc
        .perform(post("/api/appointments/APT-001/suggest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.strategyKey").value("ai"))
        .andExpect(jsonPath("$.fallbackUsed").value(false))
        .andExpect(jsonPath("$.recommendedSlotId").value("SLT-005"))
        .andExpect(jsonPath("$.confidence").value(0.88))
        .andExpect(
            jsonPath("$.reasoning").value(org.hamcrest.Matchers.containsString("Dr. Verma")));
  }

  @Test
  void aHallucinatedSlotIdFallsBackToRuleBasedWithAValidSuggestionStillCreated() throws Exception {
    // The headline T-3 assertion: the model names a slot that does not exist.
    SCRIPT.set(
        """
        {"recommendations":[{"slotId":"SLT-999","alternateProviderId":null,
          "confidence":0.99,"reasoning":"Completely invented."}]}
        """);

    mockMvc
        .perform(post("/api/appointments/APT-001/suggest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.strategyKey").value("rule"))
        .andExpect(jsonPath("$.fallbackUsed").value(true))
        .andExpect(jsonPath("$.fallbackReason").isNotEmpty())
        // and the patient still gets a real, bookable slot
        .andExpect(jsonPath("$.recommendedSlotId").value(org.hamcrest.Matchers.startsWith("SLT-")))
        .andExpect(jsonPath("$.reasoning").isNotEmpty());
  }

  @Test
  void aSlotThatIsAlreadyBookedFallsBack() throws Exception {
    SCRIPT.set(
        """
        {"recommendations":[{"slotId":"SLT-001","alternateProviderId":null,
          "confidence":0.95,"reasoning":"Suggesting a slot that is already taken."}]}
        """);

    mockMvc
        .perform(post("/api/appointments/APT-001/suggest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fallbackUsed").value(true))
        .andExpect(jsonPath("$.strategyKey").value("rule"));
  }

  @Test
  void anUnknownAlternateProviderFallsBack() throws Exception {
    SCRIPT.set(
        """
        {"recommendations":[{"slotId":"SLT-005","alternateProviderId":"PRV-999",
          "confidence":0.9,"reasoning":"Doctor who does not work here."}]}
        """);

    mockMvc
        .perform(post("/api/appointments/APT-001/suggest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fallbackUsed").value(true));
  }

  @Test
  void aGatewayTimeoutFallsBackRatherThanFailingTheRequest() throws Exception {
    FAILURE.set(new LlmUnavailableException("read timed out"));

    mockMvc
        .perform(post("/api/appointments/APT-001/suggest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.strategyKey").value("rule"))
        .andExpect(jsonPath("$.fallbackUsed").value(true))
        .andExpect(
            jsonPath("$.fallbackReason").value(org.hamcrest.Matchers.containsString("timed out")));
  }

  @Test
  void unparseableModelOutputFallsBack() throws Exception {
    SCRIPT.set("Thursday afternoon should be fine.");

    mockMvc
        .perform(post("/api/appointments/APT-001/suggest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fallbackUsed").value(true))
        .andExpect(jsonPath("$.strategyKey").value("rule"));
  }

  @Test
  void theFallbackIsExposedAsStructuredFieldsNotBuriedInTheReasoningText() throws Exception {
    // The UI must be able to style and translate "AI unavailable"; a prefixed string could only
    // ever be string-matched.
    FAILURE.set(new LlmUnavailableException("gateway down"));

    mockMvc
        .perform(post("/api/appointments/APT-001/suggest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fallbackUsed").value(true))
        .andExpect(
            jsonPath("$.reasoning")
                .value(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("unavailable —"))));
  }
}
