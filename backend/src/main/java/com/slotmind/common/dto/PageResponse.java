package com.slotmind.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Paged envelope so the frontend never depends on Spring's Page serialization. */
public record PageResponse<T>(List<T> items, int page, int size, long total, boolean hasNext) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.hasNext());
  }
}
