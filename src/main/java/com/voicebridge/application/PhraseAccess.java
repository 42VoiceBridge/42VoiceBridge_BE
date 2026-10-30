package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.port.out.PhraseRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 문구 말하기·음성 조회에서 공유하는 사용 권한 검사. 기본 문구는 누구나, 직접 추가한 문구는 본인만 쓸 수 있다. */
@Component
@RequiredArgsConstructor
public class PhraseAccess {
  private final PhraseRepositoryPort phraseRepositoryPort;

  Phrase findUsablePhrase(UUID userId, UUID phraseId) {
    if (userId == null || phraseId == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    Phrase phrase =
        phraseRepositoryPort
            .findById(phraseId)
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!phrase.isUsableBy(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }
    return phrase;
  }
}
