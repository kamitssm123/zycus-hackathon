package com.slotmind.scheduling.strategy.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.llm.LlmClient;
import com.slotmind.llm.LlmUnavailableException;
import com.slotmind.scheduling.strategy.SchedulingContext;
import com.slotmind.scheduling.strategy.SchedulingStrategy;
import com.slotmind.scheduling.strategy.SlotRecommendation;
import com.slotmind.scheduling.strategy.StrategyResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM-backed strategy.
 *
 * <p>Note what this class does NOT do: it holds no reference to the rule-based strategy and
 * contains no fallback logic. Degradation is the engine's job, so every future strategy inherits it
 * and this one stays unit-testable in isolation. It also does not check slot availability —
 * SchedulingEngine validates every strategy's output against the real candidate set, which catches
 * a hallucinated id here and a bug in a future strategy equally.
 *
 * <p>What it does own is treating the model as an untrusted input source: fences stripped, missing
 * fields tolerated, a malformed body raised as a failure rather than silently returning nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSchedulingStrategy implements SchedulingStrategy {

  public static final String KEY = "ai";

  private final LlmClient llmClient;
  private final PromptLibrary prompts;
  private final ObjectMapper objectMapper;

  @Override
  public String key() {
    return KEY;
  }

  @Override
  public StrategyResult recommend(SchedulingContext ctx) {
    if (ctx.candidateSlots().isEmpty()) {
      // Nothing to choose from; do not spend a gateway call to be told so.
      return StrategyResult.none(KEY);
    }

    String raw =
        llmClient.complete(prompts.systemPrompt(ctx.triggerReason()), prompts.userPrompt(ctx));

    JsonNode root;
    try {
      root = objectMapper.readTree(stripFences(raw));
    } catch (Exception e) {
      // Not silently dropped: raised so the engine logs and falls back.
      throw new LlmUnavailableException("LLM returned unparseable JSON: " + abbreviate(raw), e);
    }

    JsonNode recommendations = root.path("recommendations");
    if (!recommendations.isArray()) {
      throw new LlmUnavailableException(
          "LLM response has no 'recommendations' array: " + abbreviate(raw));
    }

    List<SlotRecommendation> parsed = new ArrayList<>();
    for (JsonNode node : recommendations) {
      String slotId = node.path("slotId").asText(null);
      String reasoning = node.path("reasoning").asText("");
      if (slotId == null || slotId.isBlank() || reasoning.isBlank()) {
        log.warn("dropping malformed AI recommendation: {}", node);
        continue;
      }
      JsonNode alternate = node.path("alternateProviderId");
      String alternateProviderId =
          alternate.isNull() || alternate.isMissingNode() || alternate.asText().isBlank()
              ? null
              : alternate.asText();
      parsed.add(
          new SlotRecommendation(
              slotId, alternateProviderId, node.path("confidence").asDouble(0.5), reasoning));
    }

    log.debug("AI returned {} recommendation(s) for {}", parsed.size(), ctx.appointment().id());
    return new StrategyResult(KEY, parsed);
  }

  /** Gateways wrap JSON in markdown fences more often than they should. */
  private static String stripFences(String raw) {
    String trimmed = raw == null ? "" : raw.trim();
    if (trimmed.startsWith("```")) {
      trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
    }
    return trimmed.trim();
  }

  private static String abbreviate(String raw) {
    if (raw == null) return "(null)";
    return raw.length() <= 200 ? raw : raw.substring(0, 200) + "…";
  }
}
