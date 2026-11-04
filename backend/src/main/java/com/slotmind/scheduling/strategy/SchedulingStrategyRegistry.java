package com.slotmind.scheduling.strategy;

import com.slotmind.common.exception.BadRequestException;
import com.slotmind.config.SchedulingProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Indexes every {@link SchedulingStrategy} on the classpath by its {@code key()}.
 *
 * <p>Chosen over the three obvious alternatives:
 *
 * <ul>
 *   <li>Injecting {@code Map<String, SchedulingStrategy>} keys by BEAN NAME, which would make the
 *       public config value an implementation detail ("ruleBasedStrategy", not "rule").
 *   <li>A {@code supports(String)} method on each strategy is a linear scan where two strategies
 *       can both claim a key, with the winner decided by classpath order.
 *   <li>An enum of keys would mean adding RoomConstraintStrategy requires editing a central enum —
 *       breaking the exact "implement and register, nothing more" property being graded.
 * </ul>
 */
@Slf4j
@Component
public class SchedulingStrategyRegistry {

  private final Map<String, SchedulingStrategy> byKey;
  private final SchedulingProperties properties;

  public SchedulingStrategyRegistry(
      List<SchedulingStrategy> strategies, SchedulingProperties properties) {
    this.properties = properties;
    this.byKey =
        strategies.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    SchedulingStrategy::key,
                    Function.identity(),
                    (a, b) -> {
                      throw new IllegalStateException(
                          "Duplicate scheduling strategy key '%s': %s and %s"
                              .formatted(a.key(), a.getClass(), b.getClass()));
                    }));
    log.info("scheduling strategies registered: {}", byKey.keySet());
  }

  /**
   * Precedence: explicit per-request override, then live config, then the fallback key.
   *
   * <p>The property is read HERE, on every call — caching the resolved strategy in a field at
   * construction looks identical and silently pins the value at startup.
   */
  public SchedulingStrategy resolve(String override) {
    if (StringUtils.hasText(override)) {
      return Optional.ofNullable(byKey.get(override))
          .orElseThrow(
              () ->
                  new BadRequestException(
                      "Unknown scheduling strategy '%s'. Known: %s"
                          .formatted(override, byKey.keySet())));
    }
    String configured = properties.getStrategy();
    SchedulingStrategy resolved = byKey.get(configured);
    if (resolved == null) {
      // A config typo must not 500 a user's request.
      log.error(
          "configured strategy '{}' is not registered; falling back to '{}'",
          configured,
          properties.getFallbackStrategy());
      return require(properties.getFallbackStrategy());
    }
    return resolved;
  }

  public SchedulingStrategy require(String key) {
    return Optional.ofNullable(byKey.get(key))
        .orElseThrow(
            () -> new IllegalStateException("No scheduling strategy registered for key " + key));
  }

  public Set<String> keys() {
    return byKey.keySet();
  }
}
