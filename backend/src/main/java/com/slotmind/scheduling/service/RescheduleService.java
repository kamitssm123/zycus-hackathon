package com.slotmind.scheduling.service;

import com.slotmind.common.id.IdGenerator;
import com.slotmind.scheduling.api.SuggestionResponse;
import com.slotmind.scheduling.domain.*;
import com.slotmind.scheduling.strategy.EngineResult;
import com.slotmind.scheduling.strategy.SchedulingEngine;
import com.slotmind.scheduling.strategy.SlotRecommendation;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * THE single contract. {@code POST /api/appointments/{id}/suggest} and the agentic loop both land
 * here — there is no second code path that generates suggestions, which is what makes "same
 * contract from HTTP and async" structural rather than aspirational.
 *
 * <p>{@code REQUIRES_NEW} because the async caller arrives with no transaction at all (an
 * AFTER_COMMIT listener runs outside one), and must still commit before it returns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RescheduleService {

  private final AppointmentRepository appointments;
  private final SuggestionRepository suggestions;
  private final SchedulingEngine engine;
  private final AppointmentService appointmentService;
  private final IdGenerator idGenerator;

  @Transactional(readOnly = true)
  public List<SuggestionResponse> list(SuggestionStatus status) {
    List<RescheduleSuggestion> found =
        status == null
            ? suggestions.findAllByOrderByCreatedAtDesc()
            : suggestions.findByStatusOrderByCreatedAtDesc(status);
    return found.stream().map(SuggestionResponse::from).toList();
  }

  /**
   * Generates one suggestion for an appointment. Idempotent per (appointment, triggerReason) while
   * a suggestion is still PENDING — the agentic loop can fire twice without duplicating work. The
   * app-level guard is what the H2 suite proves; uq_pending_suggestion is the DB backstop.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<SuggestionResponse> suggestFor(
      String appointmentId, TriggerReason reason, String strategyOverride) {

    if (suggestions.existsByAppointmentIdAndTriggerReasonAndStatus(
        appointmentId, reason, SuggestionStatus.PENDING)) {
      log.info("skipping {} / {} — a PENDING suggestion already exists", appointmentId, reason);
      return suggestions.findByAppointmentIdOrderByCreatedAtDesc(appointmentId).stream()
          .filter(s -> s.isPending() && s.getTriggerReason() == reason)
          .findFirst()
          .map(SuggestionResponse::from);
    }

    Appointment appointment = appointmentService.require(appointmentId);
    EngineResult result = engine.recommend(appointment, reason, strategyOverride);

    if (result.isEmpty()) {
      // Not a silent drop: logged loudly, and the appointment keeps its current status so the
      // front desk sees it unchanged rather than stuck awaiting a suggestion that will never come.
      log.warn(
          "no usable recommendation for {} ({}); appointment left as {}",
          appointmentId,
          reason,
          appointment.getStatus());
      return Optional.empty();
    }

    SlotRecommendation best = result.best();
    RescheduleSuggestion saved =
        suggestions.save(
            RescheduleSuggestion.builder()
                .id(idGenerator.next(IdGenerator.SUGGESTION))
                .appointmentId(appointmentId)
                .recommendedSlotId(best.slotId())
                .alternateProviderId(best.alternateProviderId())
                .confidence(best.confidence())
                .reasoning(best.reasoning())
                .status(SuggestionStatus.PENDING)
                .triggerReason(reason)
                .strategyKey(result.strategyKey())
                .fallbackUsed(result.fallbackUsed())
                .fallbackReason(result.fallbackReason())
                .build());

    if (appointment.getStatus() != AppointmentStatus.RESCHEDULE_PENDING) {
      appointment.transitionTo(AppointmentStatus.RESCHEDULE_PENDING);
      appointments.save(appointment);
    }

    log.info(
        "suggestion {} for {} -> slot {} (strategy={}, fallback={}, confidence={})",
        saved.getId(),
        appointmentId,
        best.slotId(),
        result.strategyKey(),
        result.fallbackUsed(),
        best.confidence());
    return Optional.of(SuggestionResponse.from(saved));
  }
}
