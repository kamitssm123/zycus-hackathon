package com.slotmind.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

  public NotFoundException(String resource, Object id) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found: " + id);
  }
}
