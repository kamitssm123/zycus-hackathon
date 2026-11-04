package com.slotmind.scheduling.service;

import com.slotmind.common.exception.BadRequestException;
import com.slotmind.common.exception.ConflictException;
import com.slotmind.common.exception.NotFoundException;
import com.slotmind.scheduling.api.SuggestionDecisionResponse;
import com.slotmind.scheduling.domain.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one atomic unit. Accepting a suggestion moves the appointment, frees the old slot and books
 * the new one, or does none of it.
 *
 * <p>This bean deliberately does NOT translate exceptions — see {@link SuggestionDecisionService}.
 * By the time a commit-time failure surfaces, this method has already returned.
 *
 * <p>REQUIRES_NEW rather than REQUIRED so the transaction provably commits before the method
 * returns, which is what lets the facade observe commit-time failures at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionAcceptanceService {

  private final SuggestionRepository suggestions;
  private final AppointmentRepository appointments;
  private final SlotRepository slots;
  private final ProviderRepository providers;

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
  public SuggestionDecisionResponse acceptInTransaction(String suggestionId) {

    // 1. The suggestion is only a pointer — no lock needed, and locking it would widen the
    //    critical section for no benefit.
    RescheduleSuggestion suggestion =
        suggestions
            .findById(suggestionId)
            .orElseThrow(() -> new NotFoundException("RescheduleSuggestion", suggestionId));
    if (!suggestion.isPending()) {
      throw new ConflictException(
          "Suggestion " + suggestionId + " is already " + suggestion.getStatus());
    }

    // 2. LOCK ORDER STEP 1 — the appointment. Global order (appointment, then slots by id
    //    ascending) is what makes a swap deadlock structurally impossible.
    Appointment appointment =
        appointments
            .lockById(suggestion.getAppointmentId())
            .orElseThrow(() -> new NotFoundException("Appointment", suggestion.getAppointmentId()));

    if (appointment.getStatus() != AppointmentStatus.RESCHEDULE_PENDING) {
      throw new ConflictException(
          "Appointment "
              + appointment.getId()
              + " is "
              + appointment.getStatus()
              + ", no longer awaiting a reschedule decision");
    }

    String oldSlotId = appointment.getSlotId();
    String newSlotId = suggestion.getRecommendedSlotId();
    if (oldSlotId.equals(newSlotId)) {
      throw new BadRequestException(
          "Suggestion " + suggestionId + " recommends the slot the appointment already holds");
    }

    // 3. LOCK ORDER STEP 2 — slots, ascending by id, one statement each. Sorting in Java rather
    //    than ORDER BY ... FOR UPDATE because Postgres lock-acquisition order depends on the plan.
    //    These must be the FIRST reads of these rows: Hibernate will not re-hydrate an already
    //    managed entity, so an earlier unlocked read would leave the race loser seeing a stale
    // OPEN.
    Map<String, Slot> locked = lockInOrder(oldSlotId, newSlotId);
    Slot oldSlot = locked.get(oldSlotId);
    Slot newSlot = locked.get(newSlotId);

    // 4. Where the race loser lands, with a message that names the slot.
    if (!newSlot.isOpen()) {
      throw new ConflictException(
          "Slot " + newSlotId + " is no longer available (" + newSlot.getStatus() + ")");
    }

    // 5. The world may have changed since the suggestion was written.
    String targetProviderId =
        suggestion.getAlternateProviderId() != null
            ? suggestion.getAlternateProviderId()
            : newSlot.getProviderId();
    Provider provider =
        providers
            .findById(targetProviderId)
            .orElseThrow(() -> new NotFoundException("Provider", targetProviderId));
    if (provider.isOffline()) {
      throw new ConflictException("Provider " + targetProviderId + " is now offline");
    }
    if (!newSlot.getProviderId().equals(targetProviderId)) {
      throw new ConflictException(
          "Slot " + newSlotId + " does not belong to provider " + targetProviderId);
    }

    // 6. Mutate. Everything involved is locked, so order no longer matters.
    //    Only release a slot that was actually BOOKED: when the trigger was SLOT_CANCELLED the old
    //    slot is already CANCELLED, and reopening it would resurrect a slot the clinic killed.
    if (oldSlot.getStatus() == SlotStatus.BOOKED) {
      oldSlot.setStatus(SlotStatus.OPEN);
    } else {
      log.info(
          "old slot {} left as {} (not reopened) while accepting {}",
          oldSlotId,
          oldSlot.getStatus(),
          suggestionId);
    }
    newSlot.setStatus(SlotStatus.BOOKED);
    appointment.moveTo(newSlotId, targetProviderId);
    suggestion.setStatus(SuggestionStatus.ACCEPTED);

    int siblings = suggestions.rejectSiblings(appointment.getId(), suggestionId);

    log.info(
        "accepted {}: {} moved {} -> {} (provider {}), {} sibling(s) rejected",
        suggestionId,
        appointment.getId(),
        oldSlotId,
        newSlotId,
        targetProviderId,
        siblings);

    return SuggestionDecisionResponse.accepted(
        suggestionId,
        appointment,
        oldSlot.getStatus() == SlotStatus.OPEN ? oldSlotId : null,
        siblings);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
  public SuggestionDecisionResponse rejectInTransaction(String suggestionId) {
    RescheduleSuggestion suggestion =
        suggestions
            .findById(suggestionId)
            .orElseThrow(() -> new NotFoundException("RescheduleSuggestion", suggestionId));
    if (!suggestion.isPending()) {
      throw new ConflictException(
          "Suggestion " + suggestionId + " is already " + suggestion.getStatus());
    }

    Appointment appointment =
        appointments
            .lockById(suggestion.getAppointmentId())
            .orElseThrow(() -> new NotFoundException("Appointment", suggestion.getAppointmentId()));

    suggestion.setStatus(SuggestionStatus.REJECTED);

    // With no other suggestion outstanding the appointment is no longer awaiting a decision; the
    // patient keeps their original slot until someone asks for another option.
    boolean othersPending =
        suggestions.findByAppointmentIdOrderByCreatedAtDesc(appointment.getId()).stream()
            .anyMatch(s -> s.isPending() && !s.getId().equals(suggestionId));
    if (!othersPending && appointment.getStatus() == AppointmentStatus.RESCHEDULE_PENDING) {
      appointment.transitionTo(AppointmentStatus.CONFIRMED);
    }

    log.info("rejected {} for {}", suggestionId, appointment.getId());
    return SuggestionDecisionResponse.rejected(suggestionId, appointment);
  }

  private Map<String, Slot> lockInOrder(String... slotIds) {
    List<String> ordered = Stream.of(slotIds).filter(Objects::nonNull).distinct().sorted().toList();
    Map<String, Slot> locked = new LinkedHashMap<>();
    for (String id : ordered) {
      locked.put(id, slots.lockById(id).orElseThrow(() -> new NotFoundException("Slot", id)));
    }
    return locked;
  }
}
