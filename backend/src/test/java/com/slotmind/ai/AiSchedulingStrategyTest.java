package com.slotmind.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.llm.LlmUnavailableException;
import com.slotmind.scheduling.domain.*;
import com.slotmind.scheduling.strategy.SchedulingContext;
import com.slotmind.scheduling.strategy.SchedulingContext.AppointmentView;
import com.slotmind.scheduling.strategy.SchedulingContext.ProviderView;
import com.slotmind.scheduling.strategy.SchedulingContext.SlotView;
import com.slotmind.scheduling.strategy.SlotRecommendation;
import com.slotmind.scheduling.strategy.ai.AiSchedulingStrategy;
import com.slotmind.scheduling.strategy.ai.PromptLibrary;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** No Spring, no network. The LlmClient fake is a lambda — it is a single-method interface. */
class AiSchedulingStrategyTest {

  private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PromptLibrary prompts = new PromptLibrary();

  private AiSchedulingStrategy strategyReturning(String canned) {
    return new AiSchedulingStrategy((sys, usr) -> canned, prompts, objectMapper);
  }

  private static SchedulingContext context(TriggerReason reason) {
    return new SchedulingContext(
        new AppointmentView(
            "APT-001",
            "John Martinez",
            AppointmentType.ROUTINE,
            "PRV-001",
            "SLT-001",
            NOW.plus(60, ChronoUnit.MINUTES)),
        List.of(
            new SlotView("SLT-005", "PRV-004", NOW.plus(150, ChronoUnit.MINUTES), 30),
            new SlotView("SLT-007", "PRV-004", NOW.plus(210, ChronoUnit.MINUTES), 30)),
        List.of(
            new ProviderView(
                "PRV-001", "Dr. Sarah Chen", Specialty.GENERAL, ProviderStatus.OFFLINE),
            new ProviderView(
                "PRV-004", "Dr. Rahul Verma", Specialty.GENERAL, ProviderStatus.AVAILABLE)),
        reason,
        NOW);
  }

  @Test
  void parsesAWellFormedResponseIntoOrderedRecommendations() {
    var result =
        strategyReturning(
                """
                {"recommendations":[
                  {"slotId":"SLT-005","alternateProviderId":"PRV-004","confidence":0.88,
                   "reasoning":"Dr. Verma can see you at 11:30 today."},
                  {"slotId":"SLT-007","alternateProviderId":"PRV-004","confidence":0.7,
                   "reasoning":"Or 12:30 if that suits better."}]}
                """)
            .recommend(context(TriggerReason.PROVIDER_OFFLINE));

    assertThat(result.strategyKey()).isEqualTo("ai");
    assertThat(result.recommendations())
        .extracting(SlotRecommendation::slotId)
        .containsExactly("SLT-005", "SLT-007");
    assertThat(result.recommendations().get(0).confidence()).isEqualTo(0.88);
  }

  @Test
  void stripsMarkdownFencesBeforeParsing() {
    var result =
        strategyReturning(
                """
                ```json
                {"recommendations":[{"slotId":"SLT-005","alternateProviderId":null,
                  "confidence":0.9,"reasoning":"Dr. Verma can see you at 11:30."}]}
                ```
                """)
            .recommend(context(TriggerReason.PROVIDER_OFFLINE));

    assertThat(result.recommendations())
        .singleElement()
        .extracting(SlotRecommendation::slotId)
        .isEqualTo("SLT-005");
  }

  @Test
  void treatsNullAlternateProviderAsNoProviderChange() {
    var result =
        strategyReturning(
                """
                {"recommendations":[{"slotId":"SLT-005","alternateProviderId":null,
                  "confidence":0.9,"reasoning":"Same doctor, new time."}]}
                """)
            .recommend(context(TriggerReason.SLOT_CANCELLED));

    assertThat(result.recommendations().get(0).alternateProviderId()).isNull();
  }

  @Test
  void unparseableJsonIsRaisedSoTheEngineCanFallBackRatherThanSilentlyDropping() {
    assertThatThrownBy(
            () ->
                strategyReturning("I think Thursday works!")
                    .recommend(context(TriggerReason.PROVIDER_OFFLINE)))
        .isInstanceOf(LlmUnavailableException.class)
        .hasMessageContaining("unparseable");
  }

  @Test
  void aResponseWithoutARecommendationsArrayIsRaised() {
    assertThatThrownBy(
            () ->
                strategyReturning("{\"answer\":\"SLT-005\"}")
                    .recommend(context(TriggerReason.PROVIDER_OFFLINE)))
        .isInstanceOf(LlmUnavailableException.class)
        .hasMessageContaining("recommendations");
  }

  @Test
  void aGatewayFailurePropagatesForTheEngineToHandle() {
    AiSchedulingStrategy strategy =
        new AiSchedulingStrategy(
            (sys, usr) -> {
              throw new LlmUnavailableException("read timed out");
            },
            prompts,
            objectMapper);

    assertThatThrownBy(() -> strategy.recommend(context(TriggerReason.PROVIDER_OFFLINE)))
        .isInstanceOf(LlmUnavailableException.class);
  }

  @Test
  void individualMalformedEntriesAreDroppedWithoutLosingTheGoodOnes() {
    var result =
        strategyReturning(
                """
                {"recommendations":[
                  {"slotId":"","confidence":0.9,"reasoning":"no id"},
                  {"slotId":"SLT-005","confidence":0.8,"reasoning":"Dr. Verma at 11:30."},
                  {"slotId":"SLT-007","confidence":0.7,"reasoning":""}]}
                """)
            .recommend(context(TriggerReason.PROVIDER_OFFLINE));

    assertThat(result.recommendations())
        .singleElement()
        .extracting(SlotRecommendation::slotId)
        .isEqualTo("SLT-005");
  }

  @Test
  void anEmptyCandidateListSkipsTheGatewayCallEntirely() {
    AtomicReference<Boolean> called = new AtomicReference<>(false);
    AiSchedulingStrategy strategy =
        new AiSchedulingStrategy(
            (sys, usr) -> {
              called.set(true);
              return "{}";
            },
            prompts,
            objectMapper);

    var empty =
        new SchedulingContext(
            new AppointmentView(
                "APT-001", "John", AppointmentType.ROUTINE, "PRV-001", "SLT-001", NOW),
            List.of(),
            List.of(),
            TriggerReason.PROVIDER_OFFLINE,
            NOW);

    assertThat(strategy.recommend(empty).isEmpty()).isTrue();
    assertThat(called.get()).as("no point paying for a call with nothing to choose from").isFalse();
  }

  @Test
  void theHallucinatedSlotSurvivesParsingButIsTheEnginesToReject() {
    // The strategy deliberately does not validate: SchedulingEngine checks every strategy's
    // output against the real candidate set, so one implementation protects all of them.
    var result =
        strategyReturning(
                """
                {"recommendations":[{"slotId":"SLT-999","alternateProviderId":null,
                  "confidence":0.99,"reasoning":"Invented slot."}]}
                """)
            .recommend(context(TriggerReason.PROVIDER_OFFLINE));

    assertThat(result.recommendations())
        .singleElement()
        .extracting(SlotRecommendation::slotId)
        .isEqualTo("SLT-999");
  }
}
