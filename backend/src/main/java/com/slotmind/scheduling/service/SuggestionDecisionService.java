package com.slotmind.scheduling.service;

import com.slotmind.common.exception.BadRequestException;
import com.slotmind.common.exception.ConflictException;
import com.slotmind.common.exception.NotFoundException;
import com.slotmind.scheduling.api.AlternativeSuggestionResponse;
import com.slotmind.scheduling.api.SuggestionDecisionResponse;
import com.slotmind.scheduling.api.SuggestionResponse;
import com.slotmind.scheduling.domain.RescheduleSuggestion;
import com.slotmind.scheduling.domain.SuggestionRepository;
import com.slotmind.scheduling.domain.SuggestionStatus;
import com.slotmind.scheduling.domain.TriggerReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Non-transactional facade whose only job is translating infrastructure failures into the project's
 * error contract.
 *
 * <p>This split is not stylistic. {@code DataIntegrityViolationException} is raised at
 * flush/commit, which happens AFTER a {@code @Transactional} method body returns — a try/catch
 * inside that method cannot observe it. And where a lock failure IS observable mid-method, Spring
 * has already marked the transaction rollback-only, so throwing {@code ConflictException} there
 * yields {@code UnexpectedRollbackException} to the caller rather than a clean 409.
 *
 * <p>Two beans rather than self-injection, because {@code this.acceptInTransaction(...)} would
 * bypass the proxy and silently run with no transaction at all — the most common bug in this shape.
 *
 * <p>Documented exception to CLAUDE.md's "services never catch-to-translate": exactly one catch
 * site, translating into the existing ConflictException. GlobalExceptionHandler remains the single
 * exit point.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionDecisionService {

  private final SuggestionAcceptanceService acceptance;
  private final SuggestionRepository suggestions;
  private final RescheduleService reschedule;

  public SuggestionDecisionResponse decide(String suggestionId, SuggestionStatus status) {
    return switch (status) {
      case ACCEPTED -> accept(suggestionId);
      case REJECTED -> reject(suggestionId);
      case PENDING ->
          throw new BadRequestException(
              "A suggestion can only be moved to ACCEPTED or REJECTED, not PENDING");
    };
  }

  public SuggestionDecisionResponse accept(String suggestionId) {
    try {
      return acceptance.acceptInTransaction(suggestionId);
    } catch (DataIntegrityViolationException e) {
      // The uq_appointments_live_slot backstop fired: someone committed first.
      log.warn(
          "accept {} lost the race at commit: {}",
          suggestionId,
          e.getMostSpecificCause().getMessage());
      throw new ConflictException(
          "That slot was just booked by someone else. Refresh and pick another option.");
    } catch (PessimisticLockingFailureException e) {
      // CannotAcquireLockException (lock timeout / deadlock victim) is a subclass of this.
      log.warn("accept {} could not acquire the slot lock", suggestionId, e);
      throw new ConflictException("That slot is being booked right now. Try again in a moment.");
    }
  }

  public SuggestionDecisionResponse reject(String suggestionId) {
    return acceptance.rejectInTransaction(suggestionId);
  }

  /**
   * Rejects a suggestion and immediately proposes a different one — the single motion a coordinator
   * actually performs when a patient says "not that time".
   *
   * <p>Two transactions, deliberately not one. The rejection is a fast, local write that must
   * commit and stay committed; generating the replacement may call an LLM over the network. Holding
   * row locks open across that call would make a slow gateway into a database problem.
   *
   * <p>The replacement reuses the ORIGINAL trigger reason rather than defaulting to INITIAL, so the
   * AI is asked the same question it was asked the first time ("the provider is gone" vs "the time
   * is gone") and the UI badge stays truthful about why this patient is being moved.
   */
  public AlternativeSuggestionResponse rejectAndSuggestAlternative(String suggestionId) {
    TriggerReason originalReason =
        suggestions
            .findById(suggestionId)
            .map(RescheduleSuggestion::getTriggerReason)
            .orElseThrow(() -> new NotFoundException("RescheduleSuggestion", suggestionId));

    SuggestionDecisionResponse rejected = reject(suggestionId);

    // The engine excludes slots this appointment has already refused, so the alternative is
    // genuinely different rather than the same answer with a new id.
    SuggestionResponse alternative =
        reschedule.suggestFor(rejected.appointmentId(), originalReason, null).orElse(null);

    if (alternative == null) {
      log.info(
          "rejected {} but no alternative remains for {}", suggestionId, rejected.appointmentId());
    }
    return new AlternativeSuggestionResponse(rejected, alternative);
  }
}
