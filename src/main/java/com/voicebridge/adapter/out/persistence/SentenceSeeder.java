package com.voicebridge.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발/시연용 임시 시드 데이터. 실제 낭독 문장 세트는 기획/AI팀이 정식으로 확정할 예정이며, 확정되면 이 클래스의 문장 목록은 교체된다.
 *
 * <p>data.sql 같은 raw SQL이 아니라 애플리케이션 코드로 넣는 이유: UUID 컬럼이 MySQL/H2에서 바이너리로 매핑되는데, 하드코딩된 SQL 리터럴로 넣으면
 * Hibernate가 런타임에 쓰는 인코딩과 어긋날 위험이 있어서, 애플리케이션 코드로 넣어 Hibernate의 UUID 처리와 항상 일치하게 한 것.
 */
@Component
@Profile("local")
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
    if (sentenceJpaRepository.count() > 0) {
      return;
    }

    List<SentenceJpaEntity> entities =
        SEED_SENTENCES.stream()
            .map(text -> SentenceJpaEntity.builder().id(UUID.randomUUID()).text(text).build())
            .toList();
    sentenceJpaRepository.saveAll(entities);
  }
}
