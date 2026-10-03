package com.voicebridge.port.in;

import java.util.UUID;

public interface GetTtsAudioUseCase {
  byte[] getAudio(UUID userId, UUID ttsId);
}
