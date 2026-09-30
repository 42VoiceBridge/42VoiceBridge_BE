package com.voicebridge.port.out;

/** Audio processing failed because of infrastructure, capacity, or its deadline. */
public class AudioProcessingException extends RuntimeException {
  public enum Reason {
    CAPACITY,
    TIMEOUT,
    INFRASTRUCTURE
  }

  private final Reason reason;

  public AudioProcessingException(Reason reason, String message) {
    super(message);
    this.reason = java.util.Objects.requireNonNull(reason);
  }

  public AudioProcessingException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = java.util.Objects.requireNonNull(reason);
  }

  public Reason reason() {
    return reason;
  }
}
