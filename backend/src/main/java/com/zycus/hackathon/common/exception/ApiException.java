package com.zycus.hackathon.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base class for every expected failure. Unexpected failures stay as plain RuntimeExceptions. */
@Getter
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}
