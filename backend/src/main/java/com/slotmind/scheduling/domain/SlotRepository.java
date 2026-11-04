package com.slotmind.scheduling.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, String> {

  /**
   * Row-level write lock. Declared explicitly because {@code @Lock} is ignored on the inherited
   * {@code findById} — that method comes from SimpleJpaRepository and is not re-proxied.
   *
   * <p>This must be the FIRST read of the row in the persistence context: Hibernate will not
   * re-hydrate an already-managed entity, so an earlier unlocked read leaves the loser of a race
   * seeing a stale OPEN status.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Slot s where s.id = :id")
  Optional<Slot> lockById(@Param("id") String id);

  List<Slot> findAllByOrderByStartTimeAsc();

  List<Slot> findByStatusOrderByStartTimeAsc(SlotStatus status);

  List<Slot> findByProviderIdOrderByStartTimeAsc(String providerId);

  List<Slot> findByStatusAndStartTimeBetweenOrderByStartTimeAsc(
      SlotStatus status, Instant from, Instant to, Pageable pageable);
}
