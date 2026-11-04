package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.Appointment;
import com.slotmind.scheduling.domain.AppointmentStatus;
import com.slotmind.scheduling.domain.AppointmentType;
import java.time.Instant;

public record AppointmentResponse(
    String id,
    String patientName,
    AppointmentType type,
    String providerId,
    String slotId,
    AppointmentStatus status,
    Instant updatedAt) {

  public static AppointmentResponse from(Appointment a) {
    return new AppointmentResponse(
        a.getId(),
        a.getPatientName(),
        a.getType(),
        a.getProviderId(),
        a.getSlotId(),
        a.getStatus(),
        a.getUpdatedAt());
  }
}
