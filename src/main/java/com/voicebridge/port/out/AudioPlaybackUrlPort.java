package com.voicebridge.port.out;

import java.util.UUID;

public interface AudioPlaybackUrlPort {
  String createUrl(String storageKey, UUID ttsId);
}
