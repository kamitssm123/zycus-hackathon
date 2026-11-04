package com.slotmind.scheduling.service;

import com.slotmind.agent.RescheduleTriggered;
import com.slotmind.common.exception.NotFoundException;
import com.slotmind.scheduling.api.SlotResponse;
import com.slotmind.scheduling.domain.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService {

  private final SlotRepository slots;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public List<SlotResponse> list(SlotStatus status, String providerId) {
    List<Slot> found;
    if (status != null) {
      found = slots.findByStatusOrderByStartTimeAsc(status);
    } else if (providerId != null) {
      found = slots.findByProviderIdOrderByStartTimeAsc(providerId);
    } else {
      found = slots.findAllByOrderByStartTimeAsc();
    }
    return found.stream().map(SlotResponse::from).toList();
  }

  /** Cancelling a slot is the second trigger for the agentic loop. */
  @Transactional
  public SlotResponse updateStatus(String id, SlotStatus status) {
    Slot slot = slots.lockById(id).orElseThrow(() -> new NotFoundException("Slot", id));
    SlotStatus previous = slot.getStatus();
    slot.setStatus(status);
    log.info("slot {} status {} -> {}", id, previous, status);

    if (status == SlotStatus.CANCELLED && previous != SlotStatus.CANCELLED) {
      events.publishEvent(RescheduleTriggered.slotCancelled(id));
    }
    return SlotResponse.from(slot);
  }
}
