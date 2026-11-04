package com.slotmind.agent;

import com.slotmind.scheduling.service.RescheduleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The agent. Perceives a change in the world, decides the scope of work without being told, acts on
 * each item, and stops at the boundary where a human must take over.
 *
 * <p>It owns no intelligence of its own — it decides WHO and WHEN, and delegates WHAT to {@link
 * RescheduleService#suggestFor}, the same method the on-demand HTTP endpoint calls. That is what
 * makes "same contract from both paths" structural rather than aspirational.
 *
 * <p>AFTER_COMMIT, not {@code @EventListener}: firing before the status change commits would build
 * suggestions against a provider status that may still roll back, producing phantom suggestions for
 * a provider who is in fact still available.
 *
 * <p>It creates PENDING suggestions and stops. It has no path to book anything — the accept
 * transaction lives in SuggestionAcceptanceService, reachable only from a human clicking Accept.
 * That boundary is enforced by which bean owns the transaction, not by a comment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RescheduleAgent {

  private final AffectedAppointmentResolver resolver;
  private final RescheduleService reschedule;

  @Async("rescheduleExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRescheduleTriggered(RescheduleTriggered event) {
    List<String> affected = resolver.resolve(event);
    log.info(
        "{} on {} affects {} appointment(s)", event.reason(), event.sourceId(), affected.size());

    for (String appointmentId : affected) {
      try {
        reschedule
            .suggestFor(appointmentId, event.reason(), null)
            .ifPresentOrElse(
                s -> log.info("agent created {} for {}", s.id(), appointmentId),
                () -> log.warn("agent produced no suggestion for {}", appointmentId));
      } catch (DataIntegrityViolationException e) {
        // Two triggers raced past the app-level dedupe check and uq_pending_suggestion caught it
        // at commit. Expected under concurrency, not an error: the suggestion already exists.
        log.warn(
            "agent skipped {} ({}) — a concurrent trigger already created the suggestion",
            appointmentId,
            event.reason());
      } catch (RuntimeException e) {
        // Nothing above catches an @Async void method. One failing appointment must not abort
        // the rest of the batch, and the failure must not vanish.
        log.error("agent failed on appointment {} ({})", appointmentId, event.reason(), e);
      }
    }
  }
}
