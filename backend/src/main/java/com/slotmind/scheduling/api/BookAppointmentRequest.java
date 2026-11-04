package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.AppointmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookAppointmentRequest(
    @NotBlank @Size(max = 120) String patientName,
    @NotNull AppointmentType type,
    @NotBlank String providerId,
    @NotBlank String slotId) {}
