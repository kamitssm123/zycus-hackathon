package com.slotmind.llm;

/** Any failure reaching or reading the gateway. The engine catches it and falls back. */
public class LlmUnavailableException extends RuntimeException {

  public LlmUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public LlmUnavailableException(String message) {
    super(message);
  }
}
