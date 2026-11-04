package com.slotmind.common.id;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

/**
 * Backs human-readable ids ("APT-005"). A mapped entity rather than a database sequence, so the
 * same mechanism exists under Flyway/Postgres and under Hibernate create-drop/H2 — a sequence
 * created by a migration simply would not exist in the test schema.
 */
@Entity
@Table(name = "id_counters")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdCounter {

  @Id
  @Column(length = 32)
  private String name;

  @Column(name = "next_value", nullable = false)
  private long nextValue;
}
