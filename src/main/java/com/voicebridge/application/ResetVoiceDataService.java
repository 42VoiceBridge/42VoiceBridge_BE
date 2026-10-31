package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.in.ResetVoiceDataUseCase;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.port.out.VoiceDataErasurePort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 음성 데이터 초기화. @Transactional을 두지 않는다 — DB 삭제는 VoiceDataErasurePort가 자기 트랜잭션에서 끝내고(커밋), 저장소 파일은 그 뒤에
 * 지운다. 파일을 먼저 지우면 DB가 실패했을 때 행은 남고 파일만 사라진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResetVoiceDataService implements ResetVoiceDataUseCase {

  private final VoiceDataErasurePort voiceDataErasurePort;
  private final StoragePort storagePort;

  @Override
  public void resetVoiceData(UUID userId) {
    if (userId == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    List<String> storageKeys = voiceDataErasurePort.eraseVoiceData(userId);
    int failed = deleteFiles(storageKeys);
    log.info("[음성 데이터 초기화] userId={} 파일 {}개 중 삭제 실패 {}개", userId, storageKeys.size(), failed);
  }

  // DB 행은 이미 지워졌으므로 되돌리지 않는다. 지우지 못한 파일은 key만 남겨 나중에 정리한다(key는 무작위 UUID라 개인정보가 없다).
  private int deleteFiles(List<String> storageKeys) {
    int failed = 0;
    for (String key : storageKeys) {
      try {
        storagePort.delete(key);
      } catch (RuntimeException e) {
        failed++;
        log.warn("[음성 데이터 초기화] 파일 삭제 실패 key={} cause={}", key, e.getClass().getSimpleName());
      }
    }
    return failed;
  }
}
