package com.slotmind.example.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, UUID> {

  Page<Item> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
