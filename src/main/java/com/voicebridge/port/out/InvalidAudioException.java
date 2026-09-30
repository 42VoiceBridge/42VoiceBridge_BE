package com.voicebridge.port.out;

/** The recording cannot be accepted; retrying inference will not fix the input. */
public class InvalidAudioException extends RuntimeException {
  public enum Reason {
    TOO_SHORT,
    TOO_LONG,
    INVALID
  }

  private final Reason reason;

  public InvalidAudioException(Reason reason, String message) {
    super(message);
    this.reason = java.util.Objects.requireNonNull(reason);
  }

  public Reason reason() {
    return reason;
  }
}
