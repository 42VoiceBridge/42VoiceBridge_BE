package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.VoiceDataErasurePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 음성 데이터를 테이블마다 지운다. 테이블 사이에 외래키가 없어(V1) DB가 대신 지워 주지 않으므로 여기서 모두 지운다.
 *
 * <p>대부분은 JPQL 일괄 삭제로 지운다. 진단 세션과 자모 오류 통계는 하위 테이블(@ElementCollection)이 있어 일괄 삭제하면 외래키에 걸리므로, 엔티티를
 * 읽어 remove해 하위 행까지 함께 지운다(사용자당 몇 개뿐이다).
 */
@Slf4j
@Component
public class VoiceDataErasureAdapter implements VoiceDataErasurePort {

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional
  public List<String> eraseVoiceData(UUID userId) {
    if (userId == null) {
      throw new IllegalArgumentException("사용자가 필요합니다.");
    }
    try {
      return erase(userId);
    } catch (DataAccessException | jakarta.persistence.PersistenceException e) {
      log.error("[음성 데이터 삭제] DB 삭제 실패 userId={}", userId, e);
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "음성 데이터를 지우지 못했습니다.");
    }
  }

  private List<String> erase(UUID userId) {
    List<String> storageKeys = new ArrayList<>();

    // 실사용 인식 → 확인 → TTS. TTS 요청에는 사용자 열이 없어 확인 문장을 거쳐 찾는다.
    List<UUID> confirmationIds =
        em.createQuery(
                "select c.id from ConfirmationJpaEntity c where c.userId = :userId", UUID.class)
            .setParameter("userId", userId)
            .getResultList();
    if (!confirmationIds.isEmpty()) {
      storageKeys.addAll(
          em.createQuery(
                  "select t.audioStorageKey from TtsRequestJpaEntity t"
                      + " where t.confirmationId in :ids and t.audioStorageKey is not null",
                  String.class)
              .setParameter("ids", confirmationIds)
              .getResultList());
      em.createQuery("delete from TtsRequestJpaEntity t where t.confirmationId in :ids")
          .setParameter("ids", confirmationIds)
          .executeUpdate();
    }
    deleteByUser("ConfirmationJpaEntity", userId);
    deleteByUser("RecognitionJpaEntity", userId);

    // 진단
    storageKeys.addAll(
        em.createQuery(
                "select r.s3Path from RecordingJpaEntity r"
                    + " where r.userId = :userId and r.s3Path is not null",
                String.class)
            .setParameter("userId", userId)
            .getResultList());
    deleteByUser("RecordingJpaEntity", userId);
    em.createQuery(
            "select s from DiagnosisSessionJpaEntity s where s.userId = :userId",
            DiagnosisSessionJpaEntity.class)
        .setParameter("userId", userId)
        .getResultList()
        .forEach(em::remove);
    JamoErrorSnapshotJpaEntity snapshot = em.find(JamoErrorSnapshotJpaEntity.class, userId);
    if (snapshot != null) {
      em.remove(snapshot);
    }
    deleteByUser("ShownPromptJpaEntity", userId);

    // 개인화
    storageKeys.addAll(
        em.createQuery(
                "select p.storageKey from PersonalizationRecordingJpaEntity p"
                    + " where p.userId = :userId and p.storageKey is not null",
                String.class)
            .setParameter("userId", userId)
            .getResultList());
    deleteByUser("PersonalizationRecordingJpaEntity", userId);
    deleteByUser("PersonalizationJobJpaEntity", userId);
    deleteByUser("PersonalizationAdapterJpaEntity", userId);

    em.flush();
    return storageKeys;
  }

  // entity는 이 클래스 안의 상수 이름만 넘긴다(외부 입력이 JPQL에 섞이지 않는다).
  private void deleteByUser(String entity, UUID userId) {
    em.createQuery("delete from " + entity + " e where e.userId = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }
}
