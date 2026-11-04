package com.slotmind.scheduling.service;

import com.slotmind.common.exception.NotFoundException;
import com.slotmind.scheduling.api.ProviderResponse;
import com.slotmind.scheduling.domain.Provider;
import com.slotmind.scheduling.domain.ProviderRepository;
import com.slotmind.scheduling.domain.ProviderStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderService {

  private final ProviderRepository providers;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public List<ProviderResponse> list() {
    return providers.findAllByOrderByIdAsc().stream().map(ProviderResponse::from).toList();
  }

  /**
   * Flipping a provider to OFFLINE is the trigger for the agentic loop. This service does not know
   * rescheduling exists — it publishes a fact and returns. The listener runs AFTER_COMMIT.
   */
  @Transactional
  public ProviderResponse updateStatus(String id, ProviderStatus status) {
    Provider provider =
        providers.findById(id).orElseThrow(() -> new NotFoundException("Provider", id));
    ProviderStatus previous = provider.getStatus();
    provider.setStatus(status);
    log.info("provider {} status {} -> {}", id, previous, status);

    if (status == ProviderStatus.OFFLINE && previous != ProviderStatus.OFFLINE) {
      // TODO(phase-5): events.publishEvent(new RescheduleTriggeredEvent(...PROVIDER_OFFLINE));
      log.info("provider {} went offline — reschedule loop will fire here", id);
    }
    return ProviderResponse.from(provider);
  }
}
