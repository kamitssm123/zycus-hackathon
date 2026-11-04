package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.Provider;
import com.slotmind.scheduling.domain.ProviderStatus;
import com.slotmind.scheduling.domain.Specialty;

public record ProviderResponse(String id, String name, Specialty specialty, ProviderStatus status) {

  public static ProviderResponse from(Provider p) {
    return new ProviderResponse(p.getId(), p.getName(), p.getSpecialty(), p.getStatus());
  }
}
