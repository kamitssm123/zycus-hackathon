package com.slotmind.scheduling.api;

import com.slotmind.common.exception.ConflictException;
import com.slotmind.scheduling.domain.SuggestionStatus;
import com.slotmind.scheduling.domain.TriggerReason;
import com.slotmind.scheduling.service.RescheduleService;
import com.slotmind.scheduling.service.SuggestionDecisionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SuggestionController {

  private final RescheduleService service;
  private final SuggestionDecisionService decisions;

  @GetMapping("/api/suggestions")
  public List<SuggestionResponse> list(@RequestParam(required = false) SuggestionStatus status) {
    return service.list(status);
  }

  /**
   * The human checkpoint. Accepting moves the appointment and swaps both slots atomically; the
   * system never books without this call.
   */
  @PatchMapping("/api/suggestions/{id}")
  public SuggestionDecisionResponse decide(
      @PathVariable String id, @Valid @RequestBody UpdateSuggestionRequest request) {
    return decisions.decide(id, request.status());
  }

  /**
   * "That one doesn't suit — what else?" One click: reject, then propose a different slot. Returns
   * the rejection plus the alternative, or a null alternative when nothing suitable is left.
   */
  @PostMapping("/api/suggestions/{id}/alternative")
  public AlternativeSuggestionResponse alternative(@PathVariable String id) {
    return decisions.rejectAndSuggestAlternative(id);
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
