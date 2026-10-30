package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기본 문구 시드 데이터. 계획서 4.7의 기획 확정 전 초안이며(결정 D1), 확정되면 이 목록을 교체한다.
 *
 * <p>SentenceSeeder와 같은 이유로 Flyway SQL이 아니라 애플리케이션 코드로 넣는다: 바이너리 UUID 리터럴이 Hibernate의 UUID 인코딩과 어긋날
 * 수 있고, 로컬은 Flyway를 쓰지 않는다.
 *
 * <p>기본 문구가 하나라도 있으면 아무것도 하지 않는다(멱등). 그래서 목록을 바꿔도 이미 넣은 환경에는 반영되지 않는다. 교체할 때는 기존 기본 문구를 지우는 작업이 따로
 * 필요하다(지우면 그 문구의 저장된 음성도 정리 대상이 된다).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "voicebridge.phrase.seed-defaults", havingValue = "true")
public class PhraseSeeder implements ApplicationRunner {

  static final Map<PhraseCategory, List<String>> DEFAULT_PHRASES = new LinkedHashMap<>();

  // 계획서 4.7 초안(우리가 직접 쓴 문장). "이 버스 ○○ 가나요?"는 TTS가 ○○를 그대로 읽으므로 넣지 않는다.
  static {
    DEFAULT_PHRASES.put(
        PhraseCategory.HOSPITAL, List.of("접수하고 싶어요.", "여기가 아파요.", "약을 처방받고 싶어요.", "천천히 말씀해 주세요."));
    DEFAULT_PHRASES.put(
        PhraseCategory.RESTAURANT,
        List.of("메뉴판 주세요.", "이거 주세요.", "물 좀 주세요.", "포장해 주세요.", "계산해 주세요."));
    DEFAULT_PHRASES.put(PhraseCategory.PHARMACY, List.of("처방전 여기 있어요.", "이 약은 어떻게 먹나요?"));
    DEFAULT_PHRASES.put(PhraseCategory.STORE, List.of("이거 얼마예요?", "봉투 주세요."));
    DEFAULT_PHRASES.put(PhraseCategory.TRANSPORT, List.of("여기서 내려 주세요."));
    DEFAULT_PHRASES.put(
        PhraseCategory.DAILY, List.of("도와주세요.", "잠시만 기다려 주세요.", "말이 느려서 천천히 들어 주세요."));
  }

  private final PhraseJpaRepository phraseJpaRepository;

  public PhraseSeeder(PhraseJpaRepository phraseJpaRepository) {
    this.phraseJpaRepository = phraseJpaRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    // 배포 후 로그만으로 기본 문구가 준비됐는지 확인할 수 있게 어느 쪽이든 남긴다.
    long existing = phraseJpaRepository.countByOwnerIdIsNull();
    if (existing > 0) {
      log.info("[문구 시드] 기본 문구가 이미 {}개 있어 넣지 않습니다.", existing);
      return;
    }

    // 도메인 생성 규칙(길이·제어문자 검사)을 그대로 거친다.
    List<PhraseJpaEntity> entities =
        DEFAULT_PHRASES.entrySet().stream()
            .flatMap(
                entry ->
                    entry.getValue().stream()
                        .map(text -> Phrase.createDefault(entry.getKey(), text)))
            .map(PhrasePersistenceAdapter::toEntity)
            .toList();
    phraseJpaRepository.saveAll(entities);
    log.info("[문구 시드] 기본 문구 {}개를 넣었습니다.", entities.size());
  }
}
