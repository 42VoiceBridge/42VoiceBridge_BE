package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.port.in.DeletePhraseUseCase;
import com.voicebridge.port.out.PhraseRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DeletePhraseService implements DeletePhraseUseCase {

  private final PhraseRepositoryPort phraseRepositoryPort;

  @Override
  public void delete(UUID userId, UUID phraseId) {
    if (userId == null || phraseId == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    Phrase phrase =
        phraseRepositoryPort
            .findById(phraseId)
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    if (phrase.isDefault()) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS, "기본 문구는 삭제할 수 없습니다.");
    }
    if (!phrase.isOwnedBy(userId)) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }
    phraseRepositoryPort.deleteById(phraseId);
  }
}
