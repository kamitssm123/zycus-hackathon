package com.slotmind.scheduling.strategy;

import com.slotmind.scheduling.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Everything a strategy is allowed to see. Built once by {@link SchedulingEngine} so the
 * synchronous HTTP path and the asynchronous agentic loop provably feed strategies identical data —
 * if each strategy loaded its own, "same contract from both paths" would be true of the interface
 * and false of the inputs.
 *
 * <p>{@code candidateSlots} is a pre-filtered, bounded list of genuinely OPEN slots. For the AI
 * strategy that list is also the primary anti-hallucination lever: the model is told to choose from
 * it, and anything outside it is rejected by the engine.
 *
 * <p>{@code now} is passed in rather than read from the clock so strategies stay pure and their
 * tests stay deterministic.
 */
public record SchedulingContext(
    AppointmentView appointment,
    List<SlotView> candidateSlots,
    List<ProviderView> providers,
    TriggerReason triggerReason,
    Instant now) {

  public SchedulingContext {
    candidateSlots = List.copyOf(candidateSlots);
    providers = List.copyOf(providers);
  }

  public Optional<ProviderView> provider(String id) {
    return providers.stream().filter(p -> p.id().equals(id)).findFirst();
  }

  public Optional<ProviderView> currentProvider() {
    return provider(appointment.providerId());
  }

  public record AppointmentView(
      String id,
      String patientName,
      AppointmentType type,
      String providerId,
      String slotId,
      Instant originalStartTime) {

    public static AppointmentView of(Appointment a, Instant originalStartTime) {
      return new AppointmentView(
          a.getId(),
          a.getPatientName(),
          a.getType(),
          a.getProviderId(),
          a.getSlotId(),
          originalStartTime);
    }

    public boolean isUrgent() {
      return type == AppointmentType.URGENT;
    }
  }

  public record SlotView(String id, String providerId, Instant startTime, int durationMinutes) {

    public static SlotView of(Slot s) {
      return new SlotView(s.getId(), s.getProviderId(), s.getStartTime(), s.getDurationMinutes());
    }
  }

  public record ProviderView(String id, String name, Specialty specialty, ProviderStatus status) {

    public static ProviderView of(Provider p) {
      return new ProviderView(p.getId(), p.getName(), p.getSpecialty(), p.getStatus());
    }
  }
}
