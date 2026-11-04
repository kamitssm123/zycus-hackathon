package com.slotmind.scheduling.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Appointment lifecycle, with the legal transitions encoded rather than implied. Keeping the state
 * machine on the enum means every caller — HTTP, the agentic loop, a future batch job — is guarded
 * by the same rules, and the rules are unit-testable without Spring.
 */
public enum AppointmentStatus {
  CONFIRMED,
  RESCHEDULE_PENDING,
  RESCHEDULED,
  COMPLETED;

  private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED =
      Map.of(
          CONFIRMED, EnumSet.of(RESCHEDULE_PENDING, COMPLETED),
          // A rejected suggestion returns the appointment to CONFIRMED, hence the edge back.
          RESCHEDULE_PENDING, EnumSet.of(RESCHEDULED, CONFIRMED, COMPLETED),
          // A rescheduled appointment can be disrupted again before it is seen.
          RESCHEDULED, EnumSet.of(RESCHEDULE_PENDING, COMPLETED),
          COMPLETED, EnumSet.noneOf(AppointmentStatus.class));

  public boolean canTransitionTo(AppointmentStatus target) {
    return ALLOWED.get(this).contains(target);
  }
}
