package com.slotmind.scheduling.strategy;

import java.util.List;

/**
 * What the engine produces: the strategy's recommendations plus the engine-level knowledge of
 * whether a fallback happened and why. Surfaced to the UI as structured fields — never folded into
 * {@code reasoning}, which the UI could then neither style nor translate, and which someone would
 * eventually string-match on.
 */
public record EngineResult(
    String strategyKey,
    boolean fallbackUsed,
    String attemptedStrategyKey,
    String fallbackReason,
    List<SlotRecommendation> recommendations) {

  public static EngineResult direct(String key, List<SlotRecommendation> recommendations) {
    return new EngineResult(key, false, null, null, recommendations);
  }

  public static EngineResult fellBack(
      String key, String attempted, String reason, List<SlotRecommendation> recommendations) {
    return new EngineResult(key, true, attempted, reason, recommendations);
  }

  public boolean isEmpty() {
    return recommendations.isEmpty();
  }

  public SlotRecommendation best() {
    return recommendations.get(0);
  }
}
