package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.TtsRequestStatus;
import com.voicebridge.port.in.GetTtsAudioUseCase;
import com.voicebridge.port.out.TtsAudioReadPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTtsAudioService implements GetTtsAudioUseCase {
  private final TtsPlaybackAccess access;
  private final TtsAudioReadPort audio;

  @Override
  public byte[] getAudio(UUID userId, UUID ttsId) {
    var request = access.findAuthorizedRequest(userId, ttsId);
    if (request.getStatus() != TtsRequestStatus.COMPLETED) {
      throw new CustomException(ErrorCode.INVALID_STATE_TRANSITION, "완료된 TTS만 재생할 수 있습니다.");
    }
    return audio.readAudio(request.getAudioStorageKey());
  }
}
