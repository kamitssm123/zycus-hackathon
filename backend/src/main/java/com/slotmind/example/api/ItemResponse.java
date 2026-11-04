package com.slotmind.example.api;

import com.slotmind.example.domain.Item;
import java.time.Instant;
import java.util.UUID;

/** Outbound payload. Entities never cross the HTTP boundary. */
public record ItemResponse(
    UUID id, String name, String description, boolean done, Instant createdAt, Instant updatedAt) {

  public static ItemResponse from(Item item) {
    return new ItemResponse(
        item.getId(),
        item.getName(),
        item.getDescription(),
        item.isDone(),
        item.getCreatedAt(),
        item.getUpdatedAt());
  }
}
