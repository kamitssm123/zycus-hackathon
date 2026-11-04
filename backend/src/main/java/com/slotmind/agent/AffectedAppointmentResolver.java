package com.slotmind.agent;

import com.slotmind.config.SchedulingProperties;
import com.slotmind.scheduling.domain.Appointment;
import com.slotmind.scheduling.domain.AppointmentRepository;
import com.slotmind.scheduling.domain.AppointmentStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONLY place the two triggers differ: provider-offline resolves 1-to-N, slot-cancelled 1-to-1.
 * Everything downstream — dedupe, engine call, fallback, persistence — is written once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AffectedAppointmentResolver {

  private final AppointmentRepository appointments;
  private final SchedulingProperties properties;

  @Transactional(readOnly = true)
  public List<String> resolve(RescheduleTriggered event) {
    List<String> ids =
        switch (event.reason()) {
          case PROVIDER_OFFLINE -> byProvider(event.sourceId());
          case SLOT_CANCELLED -> bySlot(event.sourceId());
          case INITIAL -> List.of(); // INITIAL is the on-demand HTTP path; never event-driven.
        };

    // A provider with a full day of appointments and a 2s LLM call per appointment would look like
    // a 40-second hang on stage. Cap it, and say so loudly rather than truncating silently.
    int cap = properties.getMaxFanOut();
    if (ids.size() > cap) {
      log.warn(
          "{} affected {} appointments; capping fan-out at {} — {} not processed: {}",
          event.reason(),
          ids.size(),
          cap,
          ids.size() - cap,
          ids.subList(cap, ids.size()));
      return ids.subList(0, cap);
    }
    return ids;
  }

  private List<String> byProvider(String providerId) {
    return appointments
        .findByProviderIdAndStatusOrderByIdAsc(providerId, AppointmentStatus.CONFIRMED)
        .stream()
        .map(Appointment::getId)
        .toList();
  }

  private List<String> bySlot(String slotId) {
    Optional<Appointment> occupant =
        appointments.findBySlotIdAndStatusNot(slotId, AppointmentStatus.COMPLETED);
    return occupant.map(Appointment::getId).map(List::of).orElseGet(List::of);
  }
}
