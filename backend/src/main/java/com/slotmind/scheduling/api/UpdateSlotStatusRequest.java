package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.SlotStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSlotStatusRequest(@NotNull SlotStatus status) {}
