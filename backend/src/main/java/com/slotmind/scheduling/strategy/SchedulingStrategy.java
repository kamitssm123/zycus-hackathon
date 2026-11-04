package com.slotmind.scheduling.strategy;

/**
 * The extensibility seam. Adding a strategy — RoomConstraintStrategy in sprint 2 — means
 * implementing this interface and annotating {@code @Component}. Nothing else: no enum to edit, no
 * registry to update, no fallback to wire (the engine owns that), no factory to extend.
 *
 * <p>Implementations should be pure over the context. They MAY inject a repository if they need
 * data the context cannot carry (room/equipment availability, for instance); {@code recommend} is
 * invoked inside a read-only transaction, so a strategy may read but must not write.
 */
public interface SchedulingStrategy {

  /**
   * Stable configuration key, matching {@code app.scheduling.strategy}. Deliberately not the Spring
   * bean name: a bean name is an implementation detail that must not leak into public config.
   */
  String key();

  StrategyResult recommend(SchedulingContext context);
}
