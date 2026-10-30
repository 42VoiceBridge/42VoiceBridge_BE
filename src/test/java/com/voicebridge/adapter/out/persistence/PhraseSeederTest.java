package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.domain.phrase.PhraseCategory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

class PhraseSeederTest {

  private static final long SEED_COUNT =
      PhraseSeeder.DEFAULT_PHRASES.values().stream().mapToLong(java.util.List::size).sum();

  /** 스위치를 켠 컨텍스트는 시작할 때 시드가 돈다. 이 컨텍스트만 쓰는 내장 DB라 다른 테스트에 문구가 섞이지 않는다. */
  @Nested
  @DataJpaTest
  @Import(PhraseSeeder.class)
  @TestPropertySource(properties = "voicebridge.phrase.seed-defaults=true")
  @ExtendWith(OutputCaptureExtension.class)
  class 켜져_있으면 {

    @Autowired private PhraseSeeder seeder;
    @Autowired private PhraseJpaRepository phraseJpaRepository;

    @Test
    void 빈_DB에_기본_문구를_넣고_다시_돌려도_중복되지_않는다(CapturedOutput output) {
      // 앱이 시작될 때 이미 한 번 돌았다
      assertThat(phraseJpaRepository.countByOwnerIdIsNull()).isEqualTo(SEED_COUNT);

      // 재시작과 같다
      seeder.run(null);

      assertThat(phraseJpaRepository.countByOwnerIdIsNull()).isEqualTo(SEED_COUNT);
      assertThat(output).contains("[문구 시드] 기본 문구가 이미 " + SEED_COUNT + "개 있어 넣지 않습니다.");
    }

    @Test
    void 모든_카테고리에_기본_문구가_있다() {
      assertThat(phraseJpaRepository.findAll())
          .extracting(PhraseJpaEntity::getCategory)
          .containsAll(java.util.List.of(PhraseCategory.values()));
    }
  }

  @Test
  void 스위치가_꺼져_있거나_없으면_시드가_등록되지_않는다() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withBean(PhraseJpaRepository.class, () -> Mockito.mock(PhraseJpaRepository.class))
            .withUserConfiguration(PhraseSeeder.class);

    runner.run(context -> assertThat(context).doesNotHaveBean(PhraseSeeder.class));
    runner
        .withPropertyValues("voicebridge.phrase.seed-defaults=false")
        .run(context -> assertThat(context).doesNotHaveBean(PhraseSeeder.class));
    runner
        .withPropertyValues("voicebridge.phrase.seed-defaults=true")
        .run(context -> assertThat(context).hasSingleBean(PhraseSeeder.class));
  }
}
