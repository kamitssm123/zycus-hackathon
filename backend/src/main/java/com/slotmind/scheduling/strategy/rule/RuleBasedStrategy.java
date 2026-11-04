package com.slotmind.scheduling.strategy.rule;

import com.slotmind.scheduling.domain.Specialty;
import com.slotmind.scheduling.strategy.SchedulingContext;
import com.slotmind.scheduling.strategy.SchedulingStrategy;
import com.slotmind.scheduling.strategy.SlotRecommendation;
import com.slotmind.scheduling.strategy.StrategyResult;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Deterministic baseline, and the fallback for every other strategy.
 *
 * <p>Rules, in the brief's priority order: prefer the same provider's nearest open slot; failing
 * that, the nearest open slot with a matching specialty. URGENT appointments weight proximity more
 * heavily, because waiting is the thing that actually harms an urgent patient.
 */
@Component
public class RuleBasedStrategy implements SchedulingStrategy {

  public static final String KEY = "rule";

  private static final DateTimeFormatter HUMAN_TIME =
      DateTimeFormatter.ofPattern("EEEE 'at' h:mm a").withZone(ZoneOffset.UTC);

  @Override
  public String key() {
    return KEY;
  }

  @Override
  public StrategyResult recommend(SchedulingContext ctx) {
    Optional<Specialty> wanted =
        ctx.currentProvider().map(SchedulingContext.ProviderView::specialty);

    List<SlotRecommendation> ranked =
        ctx.candidateSlots().stream()
            .map(slot -> score(ctx, slot, wanted.orElse(null)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .sorted(
                Comparator.comparingDouble(SlotRecommendation::confidence)
                    .reversed()
                    .thenComparing(r -> startTimeOf(ctx, r.slotId())))
            .toList();

    return new StrategyResult(KEY, ranked);
  }

  private Optional<SlotRecommendation> score(
      SchedulingContext ctx, SchedulingContext.SlotView slot, Specialty wanted) {

    boolean sameProvider = slot.providerId().equals(ctx.appointment().providerId());
    Optional<SchedulingContext.ProviderView> slotProvider = ctx.provider(slot.providerId());
    if (slotProvider.isEmpty()) {
      return Optional.empty();
    }
    boolean sameSpecialty = wanted != null && slotProvider.get().specialty() == wanted;

    // Confidence is computed, never hardcoded — every number here is defensible out loud.
    double base = sameProvider ? 0.95 : sameSpecialty ? 0.75 : 0.45;

    boolean sameDay =
        slot.startTime()
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .equals(ctx.appointment().originalStartTime().atZone(ZoneOffset.UTC).toLocalDate());
    double dayFactor = sameDay ? 1.0 : 0.6;

    long hoursAway = Duration.between(ctx.now(), slot.startTime()).toHours();
    double urgencyFactor = 1.0;
    if (ctx.appointment().isUrgent()) {
      // An urgent patient waiting two days is a worse outcome than a provider change.
      urgencyFactor = hoursAway <= 4 ? 1.0 : hoursAway <= 24 ? 0.8 : 0.5;
    }

    double confidence = clamp(round(base * dayFactor * urgencyFactor));
    String alternateProviderId = sameProvider ? null : slot.providerId();

    return Optional.of(
        new SlotRecommendation(
            slot.id(),
            alternateProviderId,
            confidence,
            reasoning(ctx, slot, slotProvider.get(), sameProvider, sameSpecialty)));
  }

  /** Written to be read aloud to a patient, not parsed by a machine. */
  private String reasoning(
      SchedulingContext ctx,
      SchedulingContext.SlotView slot,
      SchedulingContext.ProviderView provider,
      boolean sameProvider,
      boolean sameSpecialty) {

    String when = HUMAN_TIME.format(slot.startTime());
    String patient = ctx.appointment().patientName().split(" ")[0];

    if (sameProvider) {
      return "%s's original time is no longer available, but Dr. %s can still see them %s — same doctor, just a different time."
          .formatted(patient, lastName(provider.name()), when);
    }
    if (sameSpecialty) {
      return "Dr. %s is unavailable, so we suggest Dr. %s %s. They are also a %s doctor, so %s will get the same kind of care."
          .formatted(
              ctx.currentProvider().map(p -> lastName(p.name())).orElse("their usual doctor"),
              lastName(provider.name()),
              when,
              provider.specialty().name().toLowerCase().replace('_', ' '),
              patient);
    }
    return "The earliest opening we can offer %s is with Dr. %s %s. It is a different speciality, so please confirm this suits the visit."
        .formatted(patient, lastName(provider.name()), when);
  }

  private static String lastName(String fullName) {
    String cleaned = fullName.replaceFirst("^Dr\\.?\\s+", "").trim();
    int space = cleaned.lastIndexOf(' ');
    return space < 0 ? cleaned : cleaned.substring(space + 1);
  }

  private java.time.Instant startTimeOf(SchedulingContext ctx, String slotId) {
    return ctx.candidateSlots().stream()
        .filter(s -> s.id().equals(slotId))
        .findFirst()
        .map(SchedulingContext.SlotView::startTime)
        .orElse(java.time.Instant.MAX);
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private static double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
