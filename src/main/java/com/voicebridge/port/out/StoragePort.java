package com.voicebridge.port.out;

public interface StoragePort {
  String upload(byte[] fileBytes, String fileName);

  /** Persists a WAV under a previously reserved key. */
  void uploadAt(String key, byte[] bytes);

  /** Idempotent removal of an object owned by this service. */
  void delete(String key);
}
