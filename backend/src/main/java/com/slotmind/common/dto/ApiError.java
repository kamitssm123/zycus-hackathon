package com.slotmind.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * The ONE error shape the API ever returns. The frontend parses exactly this.
 *
 * @param code stable machine-readable code (e.g. NOT_FOUND, VALIDATION_FAILED)
 * @param message human-readable, safe to show in a toast
 * @param details per-field validation messages, null when not applicable
 * @param requestId correlation id, also present in the X-Request-Id response header
 */
public record ApiError(
    String code,
    String message,
    List<FieldIssue> details,
    String path,
    String requestId,
    Instant timestamp) {

  public record FieldIssue(String field, String message) {}
}
