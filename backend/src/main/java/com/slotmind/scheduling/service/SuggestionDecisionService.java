package com.slotmind.scheduling.service;

import com.slotmind.common.exception.BadRequestException;
import com.slotmind.common.exception.ConflictException;
import com.slotmind.scheduling.api.SuggestionDecisionResponse;
import com.slotmind.scheduling.domain.SuggestionStatus;
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
}
