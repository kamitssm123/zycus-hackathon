package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.RescheduleSuggestion;
import com.slotmind.scheduling.domain.SuggestionStatus;
import com.slotmind.scheduling.domain.TriggerReason;
import java.time.Instant;

public record SuggestionResponse(
    String id,
    String appointmentId,
    String recommendedSlotId,
    String alternateProviderId,
    double confidence,
    String reasoning,
    SuggestionStatus status,
    TriggerReason triggerReason,
    String strategyKey,
    boolean fallbackUsed,
    String fallbackReason,
    Instant createdAt) {

  public static SuggestionResponse from(RescheduleSuggestion s) {
    return new SuggestionResponse(
        s.getId(),
        s.getAppointmentId(),
        s.getRecommendedSlotId(),
        s.getAlternateProviderId(),
        s.getConfidence(),
        s.getReasoning(),
        s.getStatus(),
        s.getTriggerReason(),
        s.getStrategyKey(),
        s.isFallbackUsed(),
        s.getFallbackReason(),
        s.getCreatedAt());
  }
}
