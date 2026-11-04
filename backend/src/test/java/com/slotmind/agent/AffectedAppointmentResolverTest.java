package com.slotmind.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.slotmind.config.SchedulingProperties;
import com.slotmind.scheduling.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The one place the two triggers differ. Pure unit test. */
@ExtendWith(MockitoExtension.class)
class AffectedAppointmentResolverTest {

  @Mock private AppointmentRepository appointments;

  private SchedulingProperties properties;
  private AffectedAppointmentResolver resolver;

  private static Appointment appointment(String id) {
    return Appointment.builder()
        .id(id)
        .patientName("Patient " + id)
        .type(AppointmentType.ROUTINE)
        .providerId("PRV-001")
        .slotId("SLT-" + id.substring(4))
        .status(AppointmentStatus.CONFIRMED)
        .build();
  }

  @BeforeEach
  void setUp() {
    properties = new SchedulingProperties();
    resolver = new AffectedAppointmentResolver(appointments, properties);
  }

  @Test
  void providerOfflineResolvesEveryLiveAppointmentForThatProvider() {
    when(appointments.findByProviderIdAndStatusNotOrderByIdAsc(
            "PRV-001", AppointmentStatus.COMPLETED))
        .thenReturn(List.of(appointment("APT-001"), appointment("APT-002")));

    assertThat(resolver.resolve(RescheduleTriggered.providerOffline("PRV-001")))
        .containsExactly("APT-001", "APT-002");
  }

  @Test
  void providerOfflineIncludesPatientsAlreadyRescheduledOntoThatProvider() {
    // The bug this guards: filtering to CONFIRMED left a patient who had been moved ONTO this
    // provider invisible to the agent. They hold a real booked slot and must be moved again.
    Appointment movedHere = appointment("APT-001");
    movedHere.setStatus(AppointmentStatus.RESCHEDULED);
    when(appointments.findByProviderIdAndStatusNotOrderByIdAsc(
            "PRV-001", AppointmentStatus.COMPLETED))
        .thenReturn(List.of(movedHere));

    assertThat(resolver.resolve(RescheduleTriggered.providerOffline("PRV-001")))
        .containsExactly("APT-001");
  }

  @Test
  void providerOfflineExcludesCompletedAppointments() {
    // A visit that already happened is not disrupted by the provider leaving.
    resolver.resolve(RescheduleTriggered.providerOffline("PRV-001"));

    verify(appointments)
        .findByProviderIdAndStatusNotOrderByIdAsc("PRV-001", AppointmentStatus.COMPLETED);
    verify(appointments, never()).findAllByOrderByIdAsc();
  }

  @Test
  void slotCancelledResolvesExactlyTheOneOccupant() {
    when(appointments.findBySlotIdAndStatusNot("SLT-003", AppointmentStatus.COMPLETED))
        .thenReturn(Optional.of(appointment("APT-003")));

    assertThat(resolver.resolve(RescheduleTriggered.slotCancelled("SLT-003")))
        .containsExactly("APT-003");
  }

  @Test
  void cancellingAnEmptySlotAffectsNobody() {
    when(appointments.findBySlotIdAndStatusNot("SLT-009", AppointmentStatus.COMPLETED))
        .thenReturn(Optional.empty());

    assertThat(resolver.resolve(RescheduleTriggered.slotCancelled("SLT-009"))).isEmpty();
  }

  @Test
  void fanOutIsCappedSoABusyProviderCannotStallTheDemo() {
    List<Appointment> many =
        IntStream.rangeClosed(1, 25).mapToObj(i -> appointment("APT-%03d".formatted(i))).toList();
    when(appointments.findByProviderIdAndStatusNotOrderByIdAsc(
            "PRV-001", AppointmentStatus.COMPLETED))
        .thenReturn(many);
    properties.setMaxFanOut(10);

    assertThat(resolver.resolve(RescheduleTriggered.providerOffline("PRV-001"))).hasSize(10);
  }

  @Test
  void theInitialTriggerIsNeverEventDriven() {
    // INITIAL is the on-demand HTTP path; the agent must not act on it.
    assertThat(resolver.resolve(new RescheduleTriggered(TriggerReason.INITIAL, "APT-001")))
        .isEmpty();
    verifyNoInteractions(appointments);
  }
}
