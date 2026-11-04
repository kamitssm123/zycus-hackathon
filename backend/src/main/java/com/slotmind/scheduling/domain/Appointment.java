package com.slotmind.scheduling.domain;

import com.slotmind.common.exception.ConflictException;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "appointments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

  @Id private String id;

  @Column(name = "patient_name", nullable = false)
  private String patientName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AppointmentType type;

  @Column(name = "provider_id", nullable = false)
  private String providerId;

  @Column(name = "slot_id", nullable = false)
  private String slotId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AppointmentStatus status;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Every status change goes through here, so an illegal transition cannot be written. */
  public void transitionTo(AppointmentStatus target) {
    if (!status.canTransitionTo(target)) {
      throw new ConflictException(
          "Appointment " + id + " cannot move from " + status + " to " + target);
    }
    this.status = target;
  }

  /** Applied by the accept transaction once the target slot is locked and verified OPEN. */
  public void moveTo(String newSlotId, String newProviderId) {
    transitionTo(AppointmentStatus.RESCHEDULED);
    this.slotId = newSlotId;
    this.providerId = newProviderId;
  }
}
