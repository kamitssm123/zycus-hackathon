package com.slotmind.scheduling.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Deliberately holds {@code providerId} as a plain column rather than a {@code @ManyToOne}. The
 * accept transaction locks rows in a fixed order and must control exactly which rows it touches; an
 * association invites Hibernate to fetch a provider row we never meant to lock.
 */
@Entity
@Table(name = "slots")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Slot {

  @Id private String id;

  @Column(name = "provider_id", nullable = false)
  private String providerId;

  @Column(name = "start_time", nullable = false)
  private Instant startTime;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private SlotStatus status;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public boolean isOpen() {
    return status == SlotStatus.OPEN;
  }
}
