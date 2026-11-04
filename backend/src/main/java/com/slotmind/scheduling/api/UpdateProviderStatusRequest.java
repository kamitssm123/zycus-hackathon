package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.ProviderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProviderStatusRequest(@NotNull ProviderStatus status) {}
