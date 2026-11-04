package com.slotmind.scheduling.api;

import com.slotmind.common.exception.BadRequestException;
import com.slotmind.config.SchedulingProperties;
import com.slotmind.scheduling.strategy.SchedulingStrategyRegistry;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Flips the active strategy at runtime. Ten lines, and it is the demonstrable half of T-2: switch
 * from rule to ai live, re-run suggest, show the reasoning change without a restart.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/scheduling")
@RequiredArgsConstructor
public class SchedulingAdminController {

  private final SchedulingProperties properties;
  private final SchedulingStrategyRegistry registry;

  public record SchedulingConfigResponse(
      String strategy, String fallbackStrategy, Set<String> availableStrategies) {}

  public record SchedulingConfigRequest(@NotBlank String strategy) {}

  @GetMapping
  public SchedulingConfigResponse get() {
    return new SchedulingConfigResponse(
        properties.getStrategy(), properties.getFallbackStrategy(), registry.keys());
  }

  @PatchMapping
  public SchedulingConfigResponse update(@RequestBody SchedulingConfigRequest request) {
    if (!registry.keys().contains(request.strategy())) {
      throw new BadRequestException(
          "Unknown scheduling strategy '%s'. Known: %s"
              .formatted(request.strategy(), registry.keys()));
    }
    log.info("scheduling strategy {} -> {}", properties.getStrategy(), request.strategy());
    properties.setStrategy(request.strategy());
    return get();
  }
}
