package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.Appointment;
import com.slotmind.scheduling.domain.AppointmentStatus;
import com.slotmind.scheduling.domain.SuggestionStatus;

/** What the front desk needs to confirm the booking actually moved. */
public record SuggestionDecisionResponse(
    String suggestionId,
    SuggestionStatus suggestionStatus,
    String appointmentId,
    AppointmentStatus appointmentStatus,
    String slotId,
    String providerId,
    String releasedSlotId,
    int siblingsRejected) {

  public static SuggestionDecisionResponse accepted(
      String suggestionId, Appointment a, String releasedSlotId, int siblingsRejected) {
    return new SuggestionDecisionResponse(
        suggestionId,
        SuggestionStatus.ACCEPTED,
        a.getId(),
        a.getStatus(),
        a.getSlotId(),
        a.getProviderId(),
        releasedSlotId,
        siblingsRejected);
  }

  public static SuggestionDecisionResponse rejected(String suggestionId, Appointment a) {
    return new SuggestionDecisionResponse(
        suggestionId,
        SuggestionStatus.REJECTED,
        a.getId(),
        a.getStatus(),
        a.getSlotId(),
        a.getProviderId(),
        null,
        0);
  }
}
