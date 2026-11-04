package com.slotmind.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.slotmind.common.exception.ConflictException;
import com.slotmind.common.exception.NotFoundException;
import com.slotmind.common.id.IdGenerator;
import com.slotmind.scheduling.api.BookAppointmentRequest;
import com.slotmind.scheduling.domain.*;
import com.slotmind.scheduling.service.AppointmentService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Booking rules in isolation: no Spring context, no database. */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

  @Mock private AppointmentRepository appointments;
  @Mock private SlotRepository slots;
  @Mock private ProviderRepository providers;
  @Mock private IdGenerator idGenerator;

  @InjectMocks private AppointmentService service;

  private Provider provider;
  private Slot slot;

  @BeforeEach
  void setUp() {
    provider =
        Provider.builder()
            .id("PRV-001")
            .name("Dr. Sarah Chen")
            .specialty(Specialty.GENERAL)
            .status(ProviderStatus.AVAILABLE)
            .build();
    slot =
        Slot.builder()
            .id("SLT-001")
            .providerId("PRV-001")
            .startTime(Instant.parse("2026-11-04T10:00:00Z"))
            .durationMinutes(30)
            .status(SlotStatus.OPEN)
            .build();
  }

  private BookAppointmentRequest request() {
    return new BookAppointmentRequest(
        "John Martinez", AppointmentType.ROUTINE, "PRV-001", "SLT-001");
  }

  @Test
  void bookingAnOpenSlotMarksItBookedAndConfirmsTheAppointment() {
    when(providers.findById("PRV-001")).thenReturn(Optional.of(provider));
    when(slots.lockById("SLT-001")).thenReturn(Optional.of(slot));
    when(idGenerator.next(IdGenerator.APPOINTMENT)).thenReturn("APT-005");
    when(appointments.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

    var response = service.book(request());

    assertThat(response.id()).isEqualTo("APT-005");
    assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
    assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
  }

  @Test
  void bookingLocksTheSlotBeforeReadingItsStatus() {
    when(providers.findById("PRV-001")).thenReturn(Optional.of(provider));
    when(slots.lockById("SLT-001")).thenReturn(Optional.of(slot));
    when(idGenerator.next(any())).thenReturn("APT-005");
    when(appointments.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

    service.book(request());

    // findById would read without a row lock and race with a concurrent booking.
    verify(slots).lockById("SLT-001");
    verify(slots, never()).findById(any());
  }

  @Test
  void bookingAnAlreadyBookedSlotIsAConflict() {
    slot.setStatus(SlotStatus.BOOKED);
    when(providers.findById("PRV-001")).thenReturn(Optional.of(provider));
    when(slots.lockById("SLT-001")).thenReturn(Optional.of(slot));

    assertThatThrownBy(() -> service.book(request()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("SLT-001")
        .hasMessageContaining("BOOKED");
    verify(appointments, never()).save(any());
  }

  @Test
  void bookingWithAnOfflineProviderIsAConflict() {
    provider.setStatus(ProviderStatus.OFFLINE);
    when(providers.findById("PRV-001")).thenReturn(Optional.of(provider));

    assertThatThrownBy(() -> service.book(request()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("offline");
    verify(slots, never()).lockById(any());
  }

  @Test
  void bookingASlotBelongingToAnotherProviderIsAConflict() {
    slot.setProviderId("PRV-004");
    when(providers.findById("PRV-001")).thenReturn(Optional.of(provider));
    when(slots.lockById("SLT-001")).thenReturn(Optional.of(slot));

    assertThatThrownBy(() -> service.book(request()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("PRV-004");
  }

  @Test
  void bookingAnUnknownSlotIsNotFound() {
    when(providers.findById("PRV-001")).thenReturn(Optional.of(provider));
    when(slots.lockById("SLT-001")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.book(request())).isInstanceOf(NotFoundException.class);
  }

  @Test
  void bookingWithAnUnknownProviderIsNotFound() {
    when(providers.findById("PRV-001")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.book(request()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Provider");
  }
}
