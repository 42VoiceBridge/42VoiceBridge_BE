package com.voicebridge.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.voicebridge.common.exception.*;
import com.voicebridge.domain.recognition.*;
import com.voicebridge.port.out.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GetTtsAudioServiceTest {
  @Test
  void checksOwnerAndStateBeforeReadingBytes() {
    var repository = mock(TtsRequestRepositoryPort.class);
    var confirmations = mock(ConfirmationRepositoryPort.class);
    var audio = mock(TtsAudioReadPort.class);
    var access = new TtsPlaybackAccess(repository, confirmations);
    var service = new GetTtsAudioService(access, audio);
    UUID user = UUID.randomUUID();
    var confirmation = Confirmation.create(UUID.randomUUID(), user, "문장");
    var request = TtsRequest.create(confirmation.getId(), UUID.randomUUID());
    when(repository.findById(request.getId())).thenReturn(Optional.of(request));
    when(confirmations.findById(confirmation.getId())).thenReturn(Optional.of(confirmation));
    assertThatThrownBy(() -> service.getAudio(UUID.randomUUID(), request.getId()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FORBIDDEN_ACCESS);
    assertThatThrownBy(() -> service.getAudio(user, request.getId()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    verifyNoInteractions(audio);
    request.markCompleted("recordings/" + UUID.randomUUID() + ".mp3");
    when(audio.readAudio(request.getAudioStorageKey())).thenReturn(new byte[] {1, 2});
    assertThat(service.getAudio(user, request.getId())).containsExactly(1, 2);
    confirmation.invalidate();
    clearInvocations(audio);
    assertThatThrownBy(() -> service.getAudio(user, request.getId()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    verifyNoInteractions(audio);
  }
}
