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
 * 기본 문구 시드 데이터. 기획 확정 전 초안이며(결정 D1), 확정되면 이 목록을 교체한다.
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

  static {
    DEFAULT_PHRASES.put(
        PhraseCategory.GREETING, List.of("안녕하세요.", "감사합니다.", "죄송합니다.", "괜찮아요.", "다음에 또 만나요."));
    DEFAULT_PHRASES.put(
        PhraseCategory.REQUEST,
        List.of(
            "저는 말하기가 어려워요. 천천히 들어 주세요.",
            "잠시만 기다려 주세요.",
            "다시 한 번 말씀해 주세요.",
            "글로 써 주실 수 있나요?",
            "도와주셔서 감사합니다."));
    DEFAULT_PHRASES.put(
        PhraseCategory.DAILY,
        List.of("물 좀 주세요.", "화장실에 가고 싶어요.", "배가 고파요.", "조금 쉬고 싶어요.", "이거 얼마예요?"));
    DEFAULT_PHRASES.put(
        PhraseCategory.HEALTH,
        List.of("몸이 아파요.", "여기가 아파요.", "약을 먹어야 해요.", "병원에 가고 싶어요.", "어지러워요."));
    DEFAULT_PHRASES.put(
        PhraseCategory.EMERGENCY,
        List.of("도와주세요!", "119에 신고해 주세요.", "숨쉬기가 힘들어요.", "가족에게 연락해 주세요."));
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
