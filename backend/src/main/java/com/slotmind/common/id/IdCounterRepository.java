package com.slotmind.common.id;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdCounterRepository extends JpaRepository<IdCounter, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from IdCounter c where c.name = :name")
  Optional<IdCounter> lockByName(@Param("name") String name);
}
