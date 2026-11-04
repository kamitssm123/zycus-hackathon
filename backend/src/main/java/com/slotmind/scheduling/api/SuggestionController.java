package com.slotmind.scheduling.api;

import com.slotmind.common.exception.ConflictException;
import com.slotmind.scheduling.domain.SuggestionStatus;
import com.slotmind.scheduling.domain.TriggerReason;
import com.slotmind.scheduling.service.RescheduleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SuggestionController {

  private final RescheduleService service;

  @GetMapping("/api/suggestions")
  public List<SuggestionResponse> list(@RequestParam(required = false) SuggestionStatus status) {
    return service.list(status);
  }

  /**
   * On-demand suggestion. Same service method the agentic loop calls; {@code strategy} allows a
   * per-request override without touching global config.
   */
  @PostMapping("/api/appointments/{id}/suggest")
  public SuggestionResponse suggest(
      @PathVariable String id, @RequestParam(required = false) String strategy) {
    return service
        .suggestFor(id, TriggerReason.INITIAL, strategy)
        .orElseThrow(
            () -> new ConflictException("No open slot could be recommended for appointment " + id));
  }
}
