package com.slotmind.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.slotmind.scheduling.domain.*;
import com.slotmind.support.PostgresIT;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The highest value-per-line test in the suite.
 *
 * <p>Production runs {@code ddl-auto=validate} against a Flyway-owned schema; the H2 suite
 * generates its schema from the entities with Flyway off. Nothing else checks that the hand-written
 * migration and the entity mappings actually agree — this does, by booting the real thing. If a
 * column is renamed on one side only, the context fails to start here rather than at {@code make
 * up} two hours into the hackathon.
 */
class SchemaMigrationIT extends PostgresIT {

  @Autowired private Flyway flyway;

  /**
   * Re-applies the migrations from scratch before each assertion.
   *
   * <p>Necessary because every *IT class shares one JVM-wide container, and the concurrency IT
   * wipes the tables to build its own fixtures — so asserting seed counts against whatever the
   * previous class left behind is order-dependent and was failing in CI. Re-running clean+migrate
   * is also semantically right: this is a test ABOUT migrations, so running them is the test.
   */
  @BeforeEach
  void reapplyMigrations() {
    flyway.clean();
    flyway.migrate();
  }

  @Autowired private ProviderRepository providers;
  @Autowired private SlotRepository slots;
  @Autowired private AppointmentRepository appointments;
  @Autowired private SuggestionRepository suggestions;

  @Test
  void flywayMigratesAndHibernateValidatesAgainstRealPostgres() {
    // Reaching this line already proves ddl-auto=validate passed against the migrated schema.
    assertThat(providers.count()).isEqualTo(5);
    assertThat(slots.count()).isEqualTo(10);
    assertThat(appointments.count()).isEqualTo(4);
    assertThat(suggestions.count()).isZero();
  }

  @Test
  void seedSatisfiesTheDemoPrecondition() {
    // The walkthrough is "PATCH PRV-001 to OFFLINE -> two suggestions appear". If the seed ever
    // drifts, the demo breaks silently; this keeps that precondition under test.
    assertThat(
            appointments.findByProviderIdAndStatusOrderByIdAsc(
                "PRV-001", AppointmentStatus.CONFIRMED))
        .hasSize(2)
        .extracting(Appointment::getId)
        .containsExactly("APT-001", "APT-002");
  }

  @Test
  void openSlotsExistForAGeneralProviderSoRecommendationsArePossible() {
    assertThat(slots.findByStatusOrderByStartTimeAsc(SlotStatus.OPEN))
        .isNotEmpty()
        .anyMatch(s -> s.getProviderId().equals("PRV-004"));
  }
}
