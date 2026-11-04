package com.slotmind.scheduling.api;

import com.slotmind.scheduling.service.ProviderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {

  private final ProviderService service;

  @GetMapping
  public List<ProviderResponse> list() {
    return service.list();
  }

  /**
   * Setting OFFLINE fires the agentic re-schedule loop. Returns immediately — suggestion generation
   * happens asynchronously after this transaction commits.
   */
  @PatchMapping("/{id}/status")
  public ProviderResponse updateStatus(
      @PathVariable String id, @Valid @RequestBody UpdateProviderStatusRequest request) {
    return service.updateStatus(id, request.status());
  }
}
