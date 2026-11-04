package com.slotmind.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.slotmind.scheduling.domain.*;
import com.slotmind.scheduling.strategy.SchedulingContext;
import com.slotmind.scheduling.strategy.SchedulingContext.AppointmentView;
import com.slotmind.scheduling.strategy.SchedulingContext.ProviderView;
import com.slotmind.scheduling.strategy.SchedulingContext.SlotView;
import com.slotmind.scheduling.strategy.SlotRecommendation;
import com.slotmind.scheduling.strategy.StrategyResult;
import com.slotmind.scheduling.strategy.rule.RuleBasedStrategy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Zero Spring, zero database, fixed clock. The strategy is a pure function by design. */
class RuleBasedStrategyTest {

  private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
  private static final Instant ORIGINAL = NOW.plus(60, ChronoUnit.MINUTES);

  private final RuleBasedStrategy strategy = new RuleBasedStrategy();

  private static ProviderView provider(String id, Specialty specialty) {
    return new ProviderView(id, "Dr. " + id + " Smith", specialty, ProviderStatus.AVAILABLE);
  }

  private static SlotView slot(String id, String providerId, int minutesFromNow) {
    return new SlotView(id, providerId, NOW.plus(minutesFromNow, ChronoUnit.MINUTES), 30);
  }

  private static SchedulingContext context(
      AppointmentType type, List<SlotView> slots, List<ProviderView> providers) {
    return new SchedulingContext(
        new AppointmentView("APT-001", "John Martinez", type, "PRV-001", "SLT-001", ORIGINAL),
        slots,
        providers,
        TriggerReason.PROVIDER_OFFLINE,
        NOW);
  }

  @Test
  void prefersTheSameProvidersNearestOpenSlot() {
    StrategyResult result =
        strategy.recommend(
            context(
                AppointmentType.ROUTINE,
                List.of(slot("SLT-050", "PRV-004", 90), slot("SLT-060", "PRV-001", 180)),
                List.of(
                    provider("PRV-001", Specialty.GENERAL),
                    provider("PRV-004", Specialty.GENERAL))));

    assertThat(result.recommendations()).isNotEmpty();
    SlotRecommendation best = result.recommendations().get(0);
    assertThat(best.slotId()).isEqualTo("SLT-060");
    // Same provider => no provider change to communicate to the patient.
    assertThat(best.alternateProviderId()).isNull();
    assertThat(best.confidence()).isGreaterThan(0.9);
  }

  @Test
  void fallsBackToAMatchingSpecialtyWhenTheOwnProviderHasNothing() {
    StrategyResult result =
        strategy.recommend(
            context(
                AppointmentType.ROUTINE,
                List.of(slot("SLT-050", "PRV-004", 150), slot("SLT-070", "PRV-002", 120)),
                List.of(
                    provider("PRV-001", Specialty.GENERAL),
                    provider("PRV-004", Specialty.GENERAL),
                    provider("PRV-002", Specialty.SPECIALIST))));

    SlotRecommendation best = result.recommendations().get(0);
    assertThat(best.slotId()).isEqualTo("SLT-050");
    assertThat(best.alternateProviderId()).isEqualTo("PRV-004");
    // Specialty match scores above a mismatched specialty even when the latter is sooner.
    assertThat(best.confidence()).isGreaterThan(0.7).isLessThan(0.9);
  }

  @Test
  void ordersRecommendationsByConfidenceThenStartTime() {
    StrategyResult result =
        strategy.recommend(
            context(
                AppointmentType.ROUTINE,
                List.of(
                    slot("SLT-080", "PRV-001", 300),
                    slot("SLT-060", "PRV-001", 120),
                    slot("SLT-050", "PRV-004", 90)),
                List.of(
                    provider("PRV-001", Specialty.GENERAL),
                    provider("PRV-004", Specialty.GENERAL))));

    assertThat(result.recommendations())
        .extracting(SlotRecommendation::slotId)
        .containsExactly("SLT-060", "SLT-080", "SLT-050");
  }

  @Test
  void urgentAppointmentsPenaliseDistantSlots() {
    var slots = List.of(slot("SLT-090", "PRV-001", 120), slot("SLT-091", "PRV-001", 60 * 48));
    var providers = List.of(provider("PRV-001", Specialty.GENERAL));

    double soonUrgent =
        strategy
            .recommend(context(AppointmentType.URGENT, slots, providers))
            .recommendations()
            .get(0)
            .confidence();
    double farUrgent =
        strategy
            .recommend(context(AppointmentType.URGENT, List.of(slots.get(1)), providers))
            .recommendations()
            .get(0)
            .confidence();

    assertThat(soonUrgent).isGreaterThan(farUrgent);
  }

  @Test
  void returnsEmptyWhenThereAreNoCandidateSlots() {
    StrategyResult result =
        strategy.recommend(
            context(
                AppointmentType.ROUTINE,
                List.of(),
                List.of(provider("PRV-001", Specialty.GENERAL))));

    assertThat(result.isEmpty()).isTrue();
    assertThat(result.strategyKey()).isEqualTo("rule");
  }

  @Test
  void skipsSlotsWhoseProviderIsNotInTheRoster() {
    StrategyResult result =
        strategy.recommend(
            context(
                AppointmentType.ROUTINE,
                List.of(slot("SLT-050", "PRV-999", 90)),
                List.of(provider("PRV-001", Specialty.GENERAL))));

    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  void reasoningIsPatientFriendlyAndNamesTheDoctorAndTime() {
    StrategyResult result =
        strategy.recommend(
            context(
                AppointmentType.ROUTINE,
                List.of(slot("SLT-050", "PRV-004", 150)),
                List.of(
                    provider("PRV-001", Specialty.GENERAL),
                    provider("PRV-004", Specialty.GENERAL))));

    String reasoning = result.recommendations().get(0).reasoning();
    assertThat(reasoning).contains("Dr.").contains("John");
    // Must read as prose a receptionist can say aloud — no ids, no jargon.
    assertThat(reasoning)
        .doesNotContain("SLT-")
        .doesNotContain("PRV-")
        .doesNotContain("confidence");
  }

  @Test
  void confidenceIsAlwaysWithinZeroAndOne() {
    StrategyResult result =
        strategy.recommend(
            context(
                AppointmentType.URGENT,
                List.of(slot("SLT-050", "PRV-004", 60 * 24 * 10), slot("SLT-060", "PRV-001", 30)),
                List.of(
                    provider("PRV-001", Specialty.GENERAL),
                    provider("PRV-004", Specialty.PEDIATRIC))));

    assertThat(result.recommendations())
        .allSatisfy(r -> assertThat(r.confidence()).isBetween(0.0, 1.0));
  }
}
