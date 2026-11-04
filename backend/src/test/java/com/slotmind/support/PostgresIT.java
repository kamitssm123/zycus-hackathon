package com.slotmind.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests that need REAL Postgres: partial unique indexes, row-level locking and Flyway +
 * {@code ddl-auto=validate}. None of those exist in the H2 suite.
 *
 * <p>The container is {@code static}, so one Postgres starts per JVM and is reused across every
 * {@code *IT} class. These run under {@code mvn verify} (failsafe) only — the local Maven container
 * has no Docker socket, so CI is the oracle.
 */
@SpringBootTest
@ActiveProfiles("it")
@Testcontainers
@Import(TestFixtures.class)
public abstract class PostgresIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
