package com.slotmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mutable on purpose. {@code @ConfigurationProperties} binds at startup, but because this is a
 * singleton with setters and the registry re-reads {@code strategy} on every call, a PATCH to the
 * admin endpoint changes behaviour for the very next request — no restart, no Spring Cloud
 * {@code @RefreshScope}.
 */
@Component
@ConfigurationProperties(prefix = "app.scheduling")
@Getter
@Setter
public class SchedulingProperties {

  /** Active strategy key. Runtime-switchable. */
  private String strategy = "rule";

  /** Used when the active strategy fails or returns nothing usable. */
  private String fallbackStrategy = "rule";

  /** How far ahead to look for candidate slots. */
  private int horizonDays = 14;

  /** Caps the candidate list — and therefore the AI prompt size and cost. */
  private int maxCandidates = 50;

  /** How many recommendations survive into the response. */
  private int maxSuggestions = 3;
}
