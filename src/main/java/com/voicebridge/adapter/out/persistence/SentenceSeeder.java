package com.voicebridge.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 진단 낭독 문장 시드 데이터. 실제 낭독 문장 세트는 기획/AI팀이 정식으로 확정할 예정이며, 확정되면 이 클래스의 문장 목록은 교체된다.
 *
 * <p>data.sql 같은 raw SQL이 아니라 애플리케이션 코드로 넣는 이유: UUID 컬럼이 MySQL/H2에서 바이너리로 매핑되는데, 하드코딩된 SQL 리터럴로 넣으면
 * Hibernate가 런타임에 쓰는 인코딩과 어긋날 위험이 있어서, 애플리케이션 코드로 넣어 Hibernate의 UUID 처리와 항상 일치하게 한 것.
 *
 * <p>로컬뿐 아니라 배포 환경에서도 돈다. 문장이 없으면 진단 세션을 시작할 수 없기 때문이다(404 "낭독할 문장이 아직 준비되지 않았습니다"). 테이블이 비어 있을 때만
 * 넣으므로 재시작해도 중복되지 않는다. 대신 정식 문장 세트로 바꿀 때는 이미 들어간 문장을 교체하는 작업이 따로 필요하다.
 *
 * <p>프로필이 아니라 설정값으로 켜고 끈다. 테스트는 프로필 없이 돌아서 프로필로는 테스트에서만 끌 수 없고, 테스트가 각자 넣는 문장과 섞이면 안 된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "voicebridge.diagnosis.seed-sentences", havingValue = "true")
public class SentenceSeeder implements ApplicationRunner {

  private static final List<String> SEED_SENTENCES =
      List.of(
          "오늘 날씨가 좋습니다.",
          "물을 마시고 싶습니다.",
          "병원에 가고 싶습니다.",
          "식당이 어디예요?",
          "서울역으로 가주세요.",
          "안녕하세요, 반갑습니다.",
          "이거 얼마예요?",
          "화장실이 어디에 있나요?",
          "감사합니다.",
          "내일 다시 오겠습니다.");

  private final SentenceJpaRepository sentenceJpaRepository;

  public SentenceSeeder(SentenceJpaRepository sentenceJpaRepository) {
    this.sentenceJpaRepository = sentenceJpaRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    // 배포 후 로그만으로 문장이 준비됐는지 확인할 수 있게 어느 쪽이든 남긴다.
    long existing = sentenceJpaRepository.count();
    if (existing > 0) {
      log.info("[문장 시드] 진단 문장이 이미 {}개 있어 넣지 않습니다.", existing);
      return;
    }

    List<SentenceJpaEntity> entities =
        SEED_SENTENCES.stream()
            .map(text -> SentenceJpaEntity.builder().id(UUID.randomUUID()).text(text).build())
            .toList();
    sentenceJpaRepository.saveAll(entities);
    log.info("[문장 시드] 진단 문장 {}개를 넣었습니다.", entities.size());
  }
}
