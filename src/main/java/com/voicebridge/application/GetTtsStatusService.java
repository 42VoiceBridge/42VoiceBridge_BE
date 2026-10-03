package com.voicebridge.application;

import com.voicebridge.domain.recognition.TtsRequestStatus;
import com.voicebridge.port.in.GetTtsStatusUseCase;
import com.voicebridge.port.out.AudioPlaybackUrlPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTtsStatusService implements GetTtsStatusUseCase {
  private final TtsPlaybackAccess access;
  private final AudioPlaybackUrlPort playbackUrls;

  @Override
  public TtsStatusResult getStatus(UUID userId, UUID ttsId) {
    var request = access.findAuthorizedRequest(userId, ttsId);
    String url =
        request.getStatus() == TtsRequestStatus.COMPLETED
            ? playbackUrls.createUrl(request.getAudioStorageKey(), request.getId())
            : null;
    return new TtsStatusResult(request.getId(), request.getStatus().name(), url);
  }
}
