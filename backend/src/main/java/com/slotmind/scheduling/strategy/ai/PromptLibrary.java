package com.slotmind.scheduling.strategy.ai;

import com.slotmind.scheduling.domain.TriggerReason;
import com.slotmind.scheduling.strategy.SchedulingContext;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Two genuinely different prompts, because provider-offline and slot-cancelled are different
 * conversations with the patient.
 *
 * <p>Offline: the doctor is gone, the time may be fine — continuity of care is what is at risk, and
 * the patient needs the provider change explained warmly.
 *
 * <p>Cancelled: the doctor is fine, the time is gone — minimising disruption is what matters, and
 * changing provider would be a gratuitous downgrade.
 *
 * <p>Both enumerate the exact candidate slot IDs and forbid inventing one. That is the primary
 * anti-hallucination lever; SchedulingEngine's validation is the backstop for when it fails anyway.
 */
@Component
public class PromptLibrary {

  private static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofPattern("EEEE d MMM 'at' h:mm a").withZone(ZoneOffset.UTC);

  private static final String OUTPUT_CONTRACT =
      """
      Respond with ONLY a JSON object in exactly this shape:
      {"recommendations":[{"slotId":"<one of the CANDIDATE SLOTS ids>",
        "alternateProviderId":"<provider id, or null if the provider is unchanged>",
        "confidence":<number between 0 and 1>,
        "reasoning":"<one or two sentences a receptionist can read aloud to the patient>"}]}

      Hard rules:
      - slotId MUST be copied from the CANDIDATE SLOTS list. Never invent an id.
      - alternateProviderId MUST be null when the slot belongs to the patient's current provider.
      - reasoning is spoken to a patient: no ids, no codes, no internal jargon, no mention of
        confidence or scoring. Name the doctor and the day and time in plain words.
      - Return at most 3 recommendations, best first.
      """;

  public String systemPrompt(TriggerReason reason) {
    return switch (reason) {
      case PROVIDER_OFFLINE ->
          """
          You are a clinic scheduling assistant helping a front-desk coordinator rebook patients \
whose doctor has become unavailable at short notice.

          Your priority is continuity of care. Prefer a doctor of the same specialty, and prefer a \
time close to the patient's original appointment so they do not have to rearrange their day. \
Treat URGENT appointments as the most time-sensitive: for those, getting seen soon matters more \
than seeing a particular doctor.

          The patient is about to be told their doctor is unavailable. Explain the change warmly \
and briefly, and make clear the new doctor can provide the same care.
          """
              + OUTPUT_CONTRACT;
      case SLOT_CANCELLED ->
          """
          You are a clinic scheduling assistant helping a front-desk coordinator rebook a patient \
whose appointment slot has been cancelled. The patient's doctor is still available — only the time \
is gone.

          Strongly prefer keeping the same doctor; changing doctor here would be an unnecessary \
downgrade for the patient. Choose the nearest alternative time that minimises disruption. Only \
suggest a different doctor if that doctor has no remaining openings, and say so plainly if you do.

          The patient is about to be told their time has changed, not their doctor. Keep it short \
and reassuring.
          """
              + OUTPUT_CONTRACT;
      case INITIAL ->
          """
          You are a clinic scheduling assistant. A coordinator has asked for alternative times for \
an existing appointment. Prefer the same doctor and the smallest change to the patient's day.
          """
              + OUTPUT_CONTRACT;
    };
  }

  public String userPrompt(SchedulingContext ctx) {
    String candidates =
        ctx.candidateSlots().stream()
            .map(
                s ->
                    "  - %s | %s | Dr. %s (%s)"
                        .formatted(
                            s.id(),
                            WHEN.format(s.startTime()),
                            ctx.provider(s.providerId()).map(p -> p.name()).orElse("unknown"),
                            ctx.provider(s.providerId())
                                .map(p -> p.specialty().name())
                                .orElse("unknown")))
            .collect(Collectors.joining("\n"));

    String roster =
        ctx.providers().stream()
            .map(
                p -> "  - %s | %s | %s | %s".formatted(p.id(), p.name(), p.specialty(), p.status()))
            .collect(Collectors.joining("\n"));

    return """
        TRIGGER: %s

        APPOINTMENT
          patient: %s
          type: %s
          current doctor: %s
          original time: %s

        CANDIDATE SLOTS (choose only from these)
        %s

        PROVIDER ROSTER
        %s

        Recommend the best rebooking option for this patient.
        """
        .formatted(
            ctx.triggerReason(),
            ctx.appointment().patientName(),
            ctx.appointment().type(),
            ctx.currentProvider().map(p -> p.name() + " (" + p.specialty() + ")").orElse("unknown"),
            WHEN.format(ctx.appointment().originalStartTime()),
            candidates.isBlank() ? "  (none)" : candidates,
            roster);
  }
}
