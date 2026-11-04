package com.slotmind.scheduling.service;

import com.slotmind.common.exception.ConflictException;
import com.slotmind.common.exception.NotFoundException;
import com.slotmind.common.id.IdGenerator;
import com.slotmind.scheduling.api.AppointmentResponse;
import com.slotmind.scheduling.api.BookAppointmentRequest;
import com.slotmind.scheduling.domain.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Booking rules and transaction boundaries. Controllers hold none of this. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

  private final AppointmentRepository appointments;
  private final SlotRepository slots;
  private final ProviderRepository providers;
  private final IdGenerator idGenerator;

  @Transactional(readOnly = true)
  public List<AppointmentResponse> list(AppointmentStatus status) {
    List<Appointment> found =
        status == null
            ? appointments.findAllByOrderByIdAsc()
            : appointments.findByStatusOrderByIdAsc(status);
    return found.stream().map(AppointmentResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public AppointmentResponse get(String id) {
    return AppointmentResponse.from(require(id));
  }

  @Transactional(readOnly = true)
  public Appointment require(String id) {
    return appointments.findById(id).orElseThrow(() -> new NotFoundException("Appointment", id));
  }

  /**
   * Books an appointment onto an open slot. The slot is locked before its status is read — the same
   * discipline the accept transaction uses, because two concurrent bookings race exactly as two
   * concurrent accepts do.
   */
  @Transactional
  public AppointmentResponse book(BookAppointmentRequest request) {
    Provider provider =
        providers
            .findById(request.providerId())
            .orElseThrow(() -> new NotFoundException("Provider", request.providerId()));
    if (provider.isOffline()) {
      throw new ConflictException("Provider " + provider.getId() + " is offline");
    }

    Slot slot =
        slots
            .lockById(request.slotId())
            .orElseThrow(() -> new NotFoundException("Slot", request.slotId()));
    if (!slot.isOpen()) {
      throw new ConflictException(
          "Slot " + slot.getId() + " is not available (" + slot.getStatus() + ")");
    }
    if (!slot.getProviderId().equals(provider.getId())) {
      throw new ConflictException(
          "Slot " + slot.getId() + " belongs to provider " + slot.getProviderId());
    }

    slot.setStatus(SlotStatus.BOOKED);
    Appointment saved =
        appointments.save(
            Appointment.builder()
                .id(idGenerator.next(IdGenerator.APPOINTMENT))
                .patientName(request.patientName())
                .type(request.type())
                .providerId(provider.getId())
                .slotId(slot.getId())
                .status(AppointmentStatus.CONFIRMED)
                .build());

    log.info("booked {} for {} on slot {}", saved.getId(), saved.getPatientName(), slot.getId());
    return AppointmentResponse.from(saved);
  }
}
