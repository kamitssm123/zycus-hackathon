package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.SlotStatus;
import com.slotmind.scheduling.service.SlotService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
public class SlotController {

  private final SlotService service;

  @GetMapping
  public List<SlotResponse> list(
      @RequestParam(required = false) SlotStatus status,
      @RequestParam(required = false) String providerId) {
    return service.list(status, providerId);
  }

  /** Setting CANCELLED fires the agentic re-schedule loop for the appointment on that slot. */
  @PatchMapping("/{id}/status")
  public SlotResponse updateStatus(
      @PathVariable String id, @Valid @RequestBody UpdateSlotStatusRequest request) {
    return service.updateStatus(id, request.status());
  }
}
