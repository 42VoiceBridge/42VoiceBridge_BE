package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.AccountErasurePort;
import com.voicebridge.port.out.VoiceDataErasurePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 삭제. 음성 데이터 삭제는 VoiceDataErasurePort(다른 빈)를 불러 같은 트랜잭션에 참여시킨다 — 같은 클래스 안의 @Transactional 메서드를
 * 부르는 것이 아니므로 프록시를 거친다. 그다음 내 문구와 사용자 행을 지운다. 어느 하나라도 실패하면 모두 롤백된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountErasureAdapter implements AccountErasurePort {

  private final VoiceDataErasurePort voiceDataErasurePort;

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional
  public List<String> eraseAccount(UUID userId) {
    if (userId == null) {
      throw new IllegalArgumentException("사용자가 필요합니다.");
    }
    List<String> storageKeys = new ArrayList<>(voiceDataErasurePort.eraseVoiceData(userId));
    try {
      storageKeys.addAll(
          em.createQuery(
                  "select p.audioStorageKey from PhraseJpaEntity p"
                      + " where p.ownerId = :userId and p.audioStorageKey is not null",
                  String.class)
              .setParameter("userId", userId)
              .getResultList());
      em.createQuery("delete from PhraseJpaEntity p where p.ownerId = :userId")
          .setParameter("userId", userId)
          .executeUpdate();
      em.createQuery("delete from UserJpaEntity u where u.id = :userId")
          .setParameter("userId", userId)
          .executeUpdate();
      em.flush();
    } catch (DataAccessException | jakarta.persistence.PersistenceException e) {
      log.error("[회원 탈퇴] DB 삭제 실패 userId={}", userId, e);
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "회원 탈퇴를 처리하지 못했습니다.");
    }
    return storageKeys;
  }
}
