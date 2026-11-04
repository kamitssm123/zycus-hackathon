package com.zycus.hackathon.common.exception;

import com.zycus.hackathon.common.dto.ApiError;
import com.zycus.hackathon.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
