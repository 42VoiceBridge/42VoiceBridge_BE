package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.port.in.DeletePhraseUseCase;
import com.voicebridge.port.out.PhraseRepositoryPort;
import com.voicebridge.port.out.StoragePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 문구 삭제. 문구에 저장한 음성이 있으면 함께 지운다(사용자가 쓴 문구를 읽은 음성이라 남기지 않는다).
 *
 * <p>저장소 삭제(S3 호출)가 DB 트랜잭션을 붙잡지 않도록 클래스에 @Transactional을 걸지 않는다. 조회와 삭제는 어댑터가 각자 트랜잭션으로 끝낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeletePhraseService implements DeletePhraseUseCase {

  private final PhraseRepositoryPort phraseRepositoryPort;
  private final StoragePort storagePort;

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

    // 행을 먼저 지운다. 음성을 먼저 지우고 행 삭제가 실패하면 남은 문구가 없는 파일을 가리킨다.
    if (phrase.hasAudio()) {
      try {
        storagePort.delete(phrase.getAudioStorageKey());
      } catch (RuntimeException e) {
        log.warn("[문구 삭제] 음성 파일 삭제 실패 phraseId={}", phraseId);
      }
    }
  }
}
