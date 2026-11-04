package com.slotmind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.slotmind.scheduling.domain.*;
import com.slotmind.scheduling.strategy.SchedulingContext;
import com.slotmind.scheduling.strategy.SchedulingContext.AppointmentView;
import com.slotmind.scheduling.strategy.SchedulingContext.ProviderView;
import com.slotmind.scheduling.strategy.SchedulingContext.SlotView;
import com.slotmind.scheduling.strategy.ai.PromptLibrary;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The brief asks for two prompts because they are two different recovery situations. */
class PromptLibraryTest {

  private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
  private final PromptLibrary prompts = new PromptLibrary();

  private static SchedulingContext context() {
    return new SchedulingContext(
        new AppointmentView(
            "APT-001",
            "John Martinez",
            AppointmentType.ROUTINE,
            "PRV-001",
            "SLT-001",
            NOW.plus(60, ChronoUnit.MINUTES)),
        List.of(new SlotView("SLT-005", "PRV-004", NOW.plus(150, ChronoUnit.MINUTES), 30)),
        List.of(
            new ProviderView(
                "PRV-001", "Dr. Sarah Chen", Specialty.GENERAL, ProviderStatus.OFFLINE),
            new ProviderView(
                "PRV-004", "Dr. Rahul Verma", Specialty.GENERAL, ProviderStatus.AVAILABLE)),
        TriggerReason.PROVIDER_OFFLINE,
        NOW);
  }

  @Test
  void theTwoTriggersProduceGenuinelyDifferentPrompts() {
    String offline = prompts.systemPrompt(TriggerReason.PROVIDER_OFFLINE);
    String cancelled = prompts.systemPrompt(TriggerReason.SLOT_CANCELLED);

    assertThat(offline).isNotEqualTo(cancelled);
    // Offline is about continuity of care across a provider change...
    assertThat(offline).containsIgnoringCase("continuity of care");
    // ...cancelled is about keeping the same doctor and only moving the time.
    assertThat(cancelled).containsIgnoringCase("same doctor");
    assertThat(cancelled).containsIgnoringCase("only the time");
  }

  @Test
  void everyPromptForbidsInventingASlotId() {
    for (TriggerReason reason : TriggerReason.values()) {
      assertThat(prompts.systemPrompt(reason))
          .as("prompt for %s", reason)
          .containsIgnoringCase("Never invent an id");
    }
  }

  @Test
  void everyPromptDemandsPatientFacingLanguage() {
    for (TriggerReason reason : TriggerReason.values()) {
      assertThat(prompts.systemPrompt(reason))
          .as("prompt for %s", reason)
          .containsIgnoringCase("read aloud")
          .containsIgnoringCase("no ids");
    }
  }

  @Test
  void theUserPromptEnumeratesTheRealCandidateSlotIds() {
    // This is where anti-hallucination actually starts: the model is handed the exact list.
    String prompt = prompts.userPrompt(context());

    assertThat(prompt).contains("SLT-005").contains("Dr. Rahul Verma").contains("CANDIDATE SLOTS");
    assertThat(prompt).contains("John Martinez").contains("PROVIDER_OFFLINE");
  }

  @Test
  void theUserPromptSaysSoExplicitlyWhenThereAreNoCandidates() {
    SchedulingContext empty =
        new SchedulingContext(
            new AppointmentView(
                "APT-001", "John", AppointmentType.ROUTINE, "PRV-001", "SLT-001", NOW),
            List.of(),
            List.of(),
            TriggerReason.SLOT_CANCELLED,
            NOW);

    assertThat(prompts.userPrompt(empty)).contains("(none)");
  }
}
