package com.zycus.hackathon.health;

import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cheap liveness probe for the frontend banner and for docker-compose healthchecks. Deep checks
 * (db, redis) live at /actuator/health.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

  @Value("${spring.application.name}")
  private String appName;

  @GetMapping
  public Map<String, Object> health() {
    return Map.of("status", "UP", "service", appName, "time", Instant.now());
  }
}
