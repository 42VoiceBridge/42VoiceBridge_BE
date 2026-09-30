package com.voicebridge.port.out;

/** The recording cannot be accepted; retrying inference will not fix the input. */
public class InvalidAudioException extends RuntimeException {
  public InvalidAudioException(String message) {
    super(message);
  }
}
