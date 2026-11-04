package com.zycus.hackathon.example.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Inbound payload. Validation lives here, not in the service. */
public record ItemRequest(
    @NotBlank @Size(max = 120) String name, @Size(max = 2000) String description, Boolean done) {}
