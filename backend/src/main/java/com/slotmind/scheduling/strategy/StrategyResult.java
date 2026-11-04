package com.slotmind.scheduling.strategy;

import java.util.List;

/**
 * Ordering is the contract: index 0 is the best recommendation. There is deliberately no rank field
 * — an already-ordered list plus a rank is two sources of truth that will eventually disagree.
 *
 * <p>There are also no fallback flags here. A strategy cannot know it is a fallback; that is the
 * engine's knowledge, and putting it here would force every future strategy to populate fields it
 * has no business knowing about.
 */
public record StrategyResult(String strategyKey, List<SlotRecommendation> recommendations) {

  public StrategyResult {
    recommendations = List.copyOf(recommendations);
  }

  public static StrategyResult none(String key) {
    return new StrategyResult(key, List.of());
  }

  public boolean isEmpty() {
    return recommendations.isEmpty();
  }
}
