package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.Slot;
import com.slotmind.scheduling.domain.SlotStatus;
import java.time.Instant;

public record SlotResponse(
    String id, String providerId, Instant startTime, int durationMinutes, SlotStatus status) {

  public static SlotResponse from(Slot s) {
    return new SlotResponse(
        s.getId(), s.getProviderId(), s.getStartTime(), s.getDurationMinutes(), s.getStatus());
  }
}
