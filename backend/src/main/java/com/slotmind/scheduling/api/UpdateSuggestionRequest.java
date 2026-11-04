package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.SuggestionStatus;
import jakarta.validation.constraints.NotNull;

/** Only ACCEPTED or REJECTED are meaningful; PENDING is rejected by the service. */
public record UpdateSuggestionRequest(@NotNull SuggestionStatus status) {}
