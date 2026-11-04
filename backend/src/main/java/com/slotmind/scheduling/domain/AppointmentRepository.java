package com.slotmind.scheduling.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {

  /** See {@link SlotRepository#lockById} for why this is declared rather than inherited. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Appointment a where a.id = :id")
  Optional<Appointment> lockById(@Param("id") String id);

  List<Appointment> findByStatusOrderByIdAsc(AppointmentStatus status);

  List<Appointment> findAllByOrderByIdAsc();

  /** The agentic loop's 1-to-N resolver: every live appointment owned by a provider. */
  List<Appointment> findByProviderIdAndStatusOrderByIdAsc(
      String providerId, AppointmentStatus status);

  /** The agentic loop's 1-to-1 resolver. */
  Optional<Appointment> findBySlotIdAndStatusNot(String slotId, AppointmentStatus status);

  long countBySlotIdAndStatusNot(String slotId, AppointmentStatus status);
}
