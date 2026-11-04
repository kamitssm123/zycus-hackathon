package com.slotmind.agent;

import com.slotmind.scheduling.domain.TriggerReason;

/**
 * One event type for both triggers (AGT-1).
 *
 * <p>Provider-offline affects many appointments, slot-cancelled affects one — but that difference
 * is purely "how do I find the affected set", which is confined to {@link
 * AffectedAppointmentResolver}. Two separate event types would duplicate the fan-out, dedupe,
 * fallback and error-handling logic for no gain. A third trigger in sprint 2 (room closed) means
 * publishing this same event with a new reason; the listener is untouched.
 *
 * <p>Carries the source ID, never an entity: the listener runs after commit on another thread with
 * no persistence context, so a passed entity would arrive detached.
 */
public record RescheduleTriggered(TriggerReason reason, String sourceId) {

  public static RescheduleTriggered providerOffline(String providerId) {
    return new RescheduleTriggered(TriggerReason.PROVIDER_OFFLINE, providerId);
  }

  public static RescheduleTriggered slotCancelled(String slotId) {
    return new RescheduleTriggered(TriggerReason.SLOT_CANCELLED, slotId);
  }
}
