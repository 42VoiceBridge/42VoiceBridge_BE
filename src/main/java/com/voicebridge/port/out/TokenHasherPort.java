package com.voicebridge.port.out;

public interface TokenHasherPort {

  String hash(String rawToken);

  boolean matches(String rawToken, String hashedToken);
}
