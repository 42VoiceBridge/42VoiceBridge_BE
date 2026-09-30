package com.voicebridge.port.out;

/** Audio processing failed because of infrastructure, capacity, or its deadline. */
public class AudioProcessingException extends RuntimeException {
  public AudioProcessingException(String message) {
    super(message);
  }

  public AudioProcessingException(String message, Throwable cause) {
    super(message, cause);
  }
}
