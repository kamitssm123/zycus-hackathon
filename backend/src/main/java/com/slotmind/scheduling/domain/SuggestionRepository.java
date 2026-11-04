package com.slotmind.scheduling.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {

  List<RescheduleSuggestion> findByStatusOrderByCreatedAtDesc(SuggestionStatus status);

  List<RescheduleSuggestion> findAllByOrderByCreatedAtDesc();

  List<RescheduleSuggestion> findByAppointmentIdOrderByCreatedAtDesc(String appointmentId);

  /** The agentic loop's dedupe guard, backed by the uq_pending_suggestion partial index. */
  boolean existsByAppointmentIdAndTriggerReasonAndStatus(
      String appointmentId, TriggerReason triggerReason, SuggestionStatus status);

  long countByStatus(SuggestionStatus status);

  /**
   * Slots already promised to someone by an outstanding suggestion. The engine excludes these so a
   * fan-out does not offer the same slot to two patients — the accept transaction would let only
   * one through, and the other coordinator would make a phone call that ends in a 409.
   */
  @Query(
      """
      select s.recommendedSlotId from RescheduleSuggestion s
       where s.status = com.slotmind.scheduling.domain.SuggestionStatus.PENDING
      """)
  List<String> findSlotIdsWithPendingSuggestions();

  /**
   * Accepting one suggestion invalidates the others for that appointment.
   *
   * <p>{@code flushAutomatically} matters: without it this bulk update can run before pending
   * entity changes are flushed. {@code clearAutomatically} matters because a bulk update bypasses
   * the persistence context, leaving stale sibling entities loaded.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update RescheduleSuggestion s
         set s.status = com.slotmind.scheduling.domain.SuggestionStatus.REJECTED
       where s.appointmentId = :appointmentId
         and s.id <> :acceptedId
         and s.status = com.slotmind.scheduling.domain.SuggestionStatus.PENDING
      """)
  int rejectSiblings(
      @Param("appointmentId") String appointmentId, @Param("acceptedId") String acceptedId);
}
