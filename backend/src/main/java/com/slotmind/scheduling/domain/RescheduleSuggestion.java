package com.slotmind.scheduling.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "reschedule_suggestions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleSuggestion {

  @Id private String id;

  @Column(name = "appointment_id", nullable = false)
  private String appointmentId;

  @Column(name = "recommended_slot_id", nullable = false)
  private String recommendedSlotId;

  @Column(name = "alternate_provider_id")
  private String alternateProviderId;

  @Column(nullable = false)
  private double confidence;

  @Column(nullable = false, columnDefinition = "text")
  private String reasoning;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private SuggestionStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_reason", nullable = false, length = 24)
  private TriggerReason triggerReason;

  /** Which strategy produced this. Surfaced to the UI; never folded into {@code reasoning}. */
  @Column(name = "strategy_key", nullable = false, length = 32)
  @Builder.Default
  private String strategyKey = "rule";

  @Column(name = "fallback_used", nullable = false)
  @Builder.Default
  private boolean fallbackUsed = false;

  @Column(name = "fallback_reason", columnDefinition = "text")
  private String fallbackReason;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public boolean isPending() {
    return status == SuggestionStatus.PENDING;
  }
}
