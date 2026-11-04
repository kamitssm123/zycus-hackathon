package com.slotmind.scheduling.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, String> {

  List<Provider> findAllByOrderByIdAsc();
}
