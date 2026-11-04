package com.slotmind.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slotmind.common.exception.BadRequestException;
import com.slotmind.config.SchedulingProperties;
import com.slotmind.scheduling.strategy.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Proves the registry's two graded properties: extensibility and genuine runtime switching. */
class SchedulingStrategyRegistryTest {

  /** Stand-in for a future RoomConstraintStrategy: implement, register, nothing else. */
  private record FakeStrategy(String key) implements SchedulingStrategy {
    @Override
    public StrategyResult recommend(SchedulingContext context) {
      return StrategyResult.none(key);
    }
  }

  private SchedulingProperties properties;
  private SchedulingStrategyRegistry registry;

  @BeforeEach
  void setUp() {
    properties = new SchedulingProperties();
    registry =
        new SchedulingStrategyRegistry(
            List.of(new FakeStrategy("rule"), new FakeStrategy("ai")), properties);
  }

  @Test
  void resolvesTheConfiguredDefaultStrategy() {
    assertThat(registry.resolve(null).key()).isEqualTo("rule");
  }

  @Test
  void rereadsThePropertyOnEveryCall() {
    // The regression this guards: caching the resolved strategy in a field at construction looks
    // identical and silently pins the value at startup, making the config switch a lie.
    assertThat(registry.resolve(null).key()).isEqualTo("rule");

    properties.setStrategy("ai");

    assertThat(registry.resolve(null).key()).isEqualTo("ai");
  }

  @Test
  void perRequestOverrideBeatsTheConfiguredDefault() {
    properties.setStrategy("rule");
    assertThat(registry.resolve("ai").key()).isEqualTo("ai");
    // ...and does not mutate global config.
    assertThat(properties.getStrategy()).isEqualTo("rule");
  }

  @Test
  void unknownOverrideIsABadRequestNamingTheKnownKeys() {
    assertThatThrownBy(() -> registry.resolve("telepathy"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("telepathy")
        .hasMessageContaining("rule");
  }

  @Test
  void unknownConfiguredDefaultFallsBackInsteadOfFailingTheRequest() {
    // A config typo must degrade, not 500 a user's request.
    properties.setStrategy("typo");
    assertThat(registry.resolve(null).key()).isEqualTo("rule");
  }

  @Test
  void duplicateStrategyKeysFailFastAtConstruction() {
    assertThatThrownBy(
            () ->
                new SchedulingStrategyRegistry(
                    List.of(new FakeStrategy("rule"), new FakeStrategy("rule")), properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate scheduling strategy key");
  }

  @Test
  void registeringANewStrategyRequiresNothingBeyondImplementingTheInterface() {
    // The sprint-2 extensibility promise, asserted rather than claimed.
    var extended =
        new SchedulingStrategyRegistry(
            List.of(new FakeStrategy("rule"), new FakeStrategy("roomConstraint")), properties);

    assertThat(extended.keys()).contains("roomConstraint");
    assertThat(extended.resolve("roomConstraint").key()).isEqualTo("roomConstraint");
  }
}
