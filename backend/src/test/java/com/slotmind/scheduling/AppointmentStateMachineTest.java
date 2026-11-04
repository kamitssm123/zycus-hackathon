package com.slotmind.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slotmind.common.exception.ConflictException;
import com.slotmind.scheduling.domain.Appointment;
import com.slotmind.scheduling.domain.AppointmentStatus;
import com.slotmind.scheduling.domain.AppointmentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Pure unit test: no Spring, no database. */
class AppointmentStateMachineTest {

  private static Appointment appointment(AppointmentStatus status) {
    return Appointment.builder()
        .id("APT-001")
        .patientName("John Martinez")
        .type(AppointmentType.ROUTINE)
        .providerId("PRV-001")
        .slotId("SLT-001")
        .status(status)
        .build();
  }

  @Test
  void confirmedCanEnterReschedulePending() {
    Appointment appointment = appointment(AppointmentStatus.CONFIRMED);
    appointment.transitionTo(AppointmentStatus.RESCHEDULE_PENDING);
    assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
  }

  @Test
  void confirmedCannotJumpStraightToRescheduled() {
    assertThatThrownBy(
            () ->
                appointment(AppointmentStatus.CONFIRMED)
                    .transitionTo(AppointmentStatus.RESCHEDULED))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("CONFIRMED")
        .hasMessageContaining("RESCHEDULED");
  }

  @Test
  void rejectingASuggestionReturnsTheAppointmentToConfirmed() {
    Appointment appointment = appointment(AppointmentStatus.RESCHEDULE_PENDING);
    assertThatCode(() -> appointment.transitionTo(AppointmentStatus.CONFIRMED))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(AppointmentStatus.class)
  void completedIsTerminalForEveryTarget(AppointmentStatus target) {
    assertThat(AppointmentStatus.COMPLETED.canTransitionTo(target)).isFalse();
  }

  @Test
  void moveToSwapsSlotAndProviderAndMarksRescheduled() {
    Appointment appointment = appointment(AppointmentStatus.RESCHEDULE_PENDING);

    appointment.moveTo("SLT-005", "PRV-004");

    assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
    assertThat(appointment.getSlotId()).isEqualTo("SLT-005");
    assertThat(appointment.getProviderId()).isEqualTo("PRV-004");
  }

  @Test
  void moveToRefusesWhenTheAppointmentWasNeverPending() {
    assertThatThrownBy(() -> appointment(AppointmentStatus.CONFIRMED).moveTo("SLT-005", "PRV-004"))
        .isInstanceOf(ConflictException.class);
  }
}
