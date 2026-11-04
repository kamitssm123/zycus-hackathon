package com.slotmind.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.slotmind.common.exception.ConflictException;
import com.slotmind.scheduling.domain.*;
import com.slotmind.scheduling.service.SuggestionAcceptanceService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** The accept state machine in isolation: no Spring, no database. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuggestionAcceptanceServiceTest {

  @Mock private SuggestionRepository suggestions;
  @Mock private AppointmentRepository appointments;
  @Mock private SlotRepository slots;
  @Mock private ProviderRepository providers;

  @InjectMocks private SuggestionAcceptanceService service;

  private RescheduleSuggestion suggestion;
  private Appointment appointment;
  private Slot oldSlot;
  private Slot newSlot;
  private Provider provider;

  @BeforeEach
  void setUp() {
    provider =
        Provider.builder()
            .id("PRV-004")
            .name("Dr. Rahul Verma")
            .specialty(Specialty.GENERAL)
            .status(ProviderStatus.AVAILABLE)
            .build();
    oldSlot =
        Slot.builder()
            .id("SLT-001")
            .providerId("PRV-001")
            .startTime(Instant.parse("2026-11-04T10:00:00Z"))
            .durationMinutes(30)
            .status(SlotStatus.BOOKED)
            .build();
    newSlot =
        Slot.builder()
            .id("SLT-005")
            .providerId("PRV-004")
            .startTime(Instant.parse("2026-11-04T13:30:00Z"))
            .durationMinutes(30)
            .status(SlotStatus.OPEN)
            .build();
    appointment =
        Appointment.builder()
            .id("APT-001")
            .patientName("John Martinez")
            .type(AppointmentType.ROUTINE)
            .providerId("PRV-001")
            .slotId("SLT-001")
            .status(AppointmentStatus.RESCHEDULE_PENDING)
            .build();
    suggestion =
        RescheduleSuggestion.builder()
            .id("SGT-001")
            .appointmentId("APT-001")
            .recommendedSlotId("SLT-005")
            .alternateProviderId("PRV-004")
            .confidence(0.75)
            .reasoning("…")
            .status(SuggestionStatus.PENDING)
            .triggerReason(TriggerReason.PROVIDER_OFFLINE)
            .build();

    when(suggestions.findById("SGT-001")).thenReturn(Optional.of(suggestion));
    when(appointments.lockById("APT-001")).thenReturn(Optional.of(appointment));
    when(slots.lockById("SLT-001")).thenReturn(Optional.of(oldSlot));
    when(slots.lockById("SLT-005")).thenReturn(Optional.of(newSlot));
    when(providers.findById("PRV-004")).thenReturn(Optional.of(provider));
    when(suggestions.findByAppointmentIdOrderByCreatedAtDesc("APT-001"))
        .thenReturn(List.of(suggestion));
  }

  @Test
  void acceptMovesTheAppointmentAndSwapsBothSlots() {
    var response = service.acceptInTransaction("SGT-001");

    assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
    assertThat(appointment.getSlotId()).isEqualTo("SLT-005");
    assertThat(appointment.getProviderId()).isEqualTo("PRV-004");
    assertThat(oldSlot.getStatus()).isEqualTo(SlotStatus.OPEN);
    assertThat(newSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);
    assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
    assertThat(response.releasedSlotId()).isEqualTo("SLT-001");
  }

  @Test
  void acceptLocksEveryRowItMutates() {
    service.acceptInTransaction("SGT-001");

    // An unlocked read would return a stale entity and lose the race silently.
    verify(appointments).lockById("APT-001");
    verify(slots).lockById("SLT-001");
    verify(slots).lockById("SLT-005");
    verify(slots, never()).findById(anyString());
    verify(appointments, never()).findById(anyString());
  }

  @Test
  void acceptRejectsSiblingSuggestionsForTheSameAppointment() {
    service.acceptInTransaction("SGT-001");
    verify(suggestions).rejectSiblings("APT-001", "SGT-001");
  }

  @Test
  void acceptingAnAlreadyAcceptedSuggestionIsAConflict() {
    suggestion.setStatus(SuggestionStatus.ACCEPTED);

    assertThatThrownBy(() -> service.acceptInTransaction("SGT-001"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("already ACCEPTED");
    verify(appointments, never()).lockById(any());
  }

  @Test
  void acceptWhenTheSlotIsNoLongerOpenIsAConflictAndLeavesTheAppointmentUntouched() {
    // This is the path a race loser takes.
    newSlot.setStatus(SlotStatus.BOOKED);

    assertThatThrownBy(() -> service.acceptInTransaction("SGT-001"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("SLT-005")
        .hasMessageContaining("no longer available");

    assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
    assertThat(appointment.getSlotId()).isEqualTo("SLT-001");
    assertThat(oldSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);
  }

  @Test
  void acceptWhenTheAppointmentIsNoLongerPendingIsAConflict() {
    appointment.setStatus(AppointmentStatus.CONFIRMED);

    assertThatThrownBy(() -> service.acceptInTransaction("SGT-001"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("no longer awaiting");
  }

  @Test
  void acceptWhenTheTargetProviderWentOfflineIsAConflict() {
    provider.setStatus(ProviderStatus.OFFLINE);

    assertThatThrownBy(() -> service.acceptInTransaction("SGT-001"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("now offline");
  }

  @Test
  void aCancelledOldSlotIsNotResurrected() {
    // SLOT_CANCELLED trigger: the clinic deliberately killed that slot. Reopening it would
    // let the next patient be booked into a time that does not exist.
    oldSlot.setStatus(SlotStatus.CANCELLED);
    suggestion.setTriggerReason(TriggerReason.SLOT_CANCELLED);

    var response = service.acceptInTransaction("SGT-001");

    assertThat(oldSlot.getStatus()).isEqualTo(SlotStatus.CANCELLED);
    assertThat(response.releasedSlotId()).isNull();
    assertThat(newSlot.getStatus()).isEqualTo(SlotStatus.BOOKED);
  }

  @Test
  void rejectLeavesTheAppointmentOnItsOriginalSlot() {
    var response = service.rejectInTransaction("SGT-001");

    assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.REJECTED);
    assertThat(appointment.getSlotId()).isEqualTo("SLT-001");
    assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    assertThat(response.suggestionStatus()).isEqualTo(SuggestionStatus.REJECTED);
  }

  @Test
  void rejectKeepsTheAppointmentPendingWhileAnotherSuggestionIsOutstanding() {
    RescheduleSuggestion other =
        RescheduleSuggestion.builder()
            .id("SGT-002")
            .appointmentId("APT-001")
            .recommendedSlotId("SLT-007")
            .confidence(0.6)
            .reasoning("…")
            .status(SuggestionStatus.PENDING)
            .triggerReason(TriggerReason.INITIAL)
            .build();
    when(suggestions.findByAppointmentIdOrderByCreatedAtDesc("APT-001"))
        .thenReturn(List.of(suggestion, other));

    service.rejectInTransaction("SGT-001");

    assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULE_PENDING);
  }
}
