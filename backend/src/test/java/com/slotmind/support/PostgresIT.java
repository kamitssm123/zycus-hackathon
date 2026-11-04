package com.slotmind.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for tests needing REAL Postgres: partial unique indexes, row-level locking, and Flyway with
 * {@code ddl-auto=validate}. None of those exist in the H2 suite.
 *
 * <p><strong>Singleton container pattern, deliberately.</strong> The container is started once in a
 * static initialiser and is NOT annotated {@code @Container}, and the class is NOT annotated
 * {@code @Testcontainers}. That combination would hand lifecycle to the JUnit extension, which
 * stops the container when its first test class finishes — while Spring's context cache happily
 * reuses the same DataSource for the next {@code *IT} class, which then times out opening a
 * connection to a database that no longer exists.
 *
 * <p>That is not hypothetical: it is exactly how this failed in CI. SchemaMigrationIT errored with
 * {@code CannotCreateTransaction} after AcceptSuggestionConcurrencyIT had shut the container down.
 * Started this way the container lives for the JVM, and Ryuk reaps it at exit.
 */
@SpringBootTest
@ActiveProfiles("it")
@Import(TestFixtures.class)
public abstract class PostgresIT {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }
}
