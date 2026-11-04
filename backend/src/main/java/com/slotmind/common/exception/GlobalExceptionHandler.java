package com.slotmind.common.exception;

import com.slotmind.common.dto.ApiError;
import com.slotmind.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Single exit point for every error response. Controllers never build error bodies, never catch to
 * translate — they throw, this translates.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
    log.warn("handled: {} {}", ex.getCode(), ex.getMessage());
    return build(ex.getStatus(), ex.getCode(), ex.getMessage(), null, req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    List<ApiError.FieldIssue> issues =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiError.FieldIssue(f.getField(), f.getDefaultMessage()))
            .toList();
    return build(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", issues, req);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraint(
      ConstraintViolationException ex, HttpServletRequest req) {
    List<ApiError.FieldIssue> issues =
        ex.getConstraintViolations().stream()
            .map(v -> new ApiError.FieldIssue(String.valueOf(v.getPropertyPath()), v.getMessage()))
            .toList();
    return build(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", issues, req);
  }

  /**
   * An unparseable body — a bad enum value, malformed JSON, a wrong field type. This is the
   * client's mistake, not ours: without this handler it falls through to handleUnexpected and
   * returns 500 with a stack trace in the log for what is really a typo.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest req) {
    log.warn(
        "unreadable request body on {} {}: {}",
        req.getMethod(),
        req.getRequestURI(),
        ex.getMessage());
    return build(
        HttpStatus.BAD_REQUEST,
        "MALFORMED_REQUEST",
        "Request body could not be parsed. Check field types and enum values.",
        null,
        req);
  }

  /** A path or query parameter that cannot be converted — e.g. ?status=NOPE on a filter. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiError> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
    List<ApiError.FieldIssue> issues =
        List.of(new ApiError.FieldIssue(ex.getName(), "has an unsupported value"));
    return build(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", issues, req);
  }

  /** Last resort. Logs the stack trace, leaks nothing to the client. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
    log.error("unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Something went wrong. Quote the requestId when reporting this.",
        null,
        req);
  }

  private ResponseEntity<ApiError> build(
      HttpStatus status,
      String code,
      String message,
      List<ApiError.FieldIssue> details,
      HttpServletRequest req) {
    ApiError body =
        new ApiError(
            code,
            message,
            details,
            req.getRequestURI(),
            MDC.get(RequestIdFilter.MDC_KEY),
            Instant.now());
    return ResponseEntity.status(status).body(body);
  }
}
