package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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

class SentenceSeederTest {

  /** 스위치를 켠 컨텍스트는 시작할 때 시드가 돈다. 이 컨텍스트만 쓰는 내장 DB라 다른 테스트에 문장이 섞이지 않는다. */
  @Nested
  @DataJpaTest
  @Import(SentenceSeeder.class)
  @TestPropertySource(properties = "voicebridge.diagnosis.seed-sentences=true")
  @ExtendWith(OutputCaptureExtension.class)
  class 켜져_있으면 {

    @Autowired private SentenceSeeder seeder;
    @Autowired private SentenceJpaRepository sentenceJpaRepository;

    @Test
    void 빈_DB에_진단_문장을_넣고_다시_돌려도_중복되지_않는다(CapturedOutput output) {
      // 앱이 시작될 때 이미 한 번 돌았다
      assertThat(sentenceJpaRepository.count()).isEqualTo(10);

      // 재시작과 같다
      seeder.run(null);

      assertThat(sentenceJpaRepository.count()).isEqualTo(10);
      // 배포 후 로그로 문장 준비 상태를 확인할 수 있어야 한다
      assertThat(output).contains("[문장 시드] 진단 문장이 이미 10개 있어 넣지 않습니다.");
    }
  }

  @Test
  void 스위치가_꺼져_있거나_없으면_시드가_등록되지_않는다() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withBean(SentenceJpaRepository.class, () -> Mockito.mock(SentenceJpaRepository.class))
            .withUserConfiguration(SentenceSeeder.class);

    runner.run(context -> assertThat(context).doesNotHaveBean(SentenceSeeder.class));
    runner
        .withPropertyValues("voicebridge.diagnosis.seed-sentences=false")
        .run(context -> assertThat(context).doesNotHaveBean(SentenceSeeder.class));
    runner
        .withPropertyValues("voicebridge.diagnosis.seed-sentences=true")
        .run(context -> assertThat(context).hasSingleBean(SentenceSeeder.class));
  }
}
