package com.voicebridge.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.voicebridge.application.PhraseAccess;
import com.voicebridge.port.in.GetPhraseAudioUseCase;
import com.voicebridge.port.out.TtsAudioReadPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalPhrasePlaybackConfigTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(LocalPhrasePlaybackConfig.class)
          .withBean(PhraseAccess.class, () -> mock(PhraseAccess.class))
          .withBean(TtsAudioReadPort.class, () -> mock(TtsAudioReadPort.class));

  @Test
  void 문구_음성_조회는_local_프로파일에서만_등록한다() {
    context
        .withInitializer(c -> c.getEnvironment().setActiveProfiles("local"))
        .run(c -> assertThat(c).hasSingleBean(GetPhraseAudioUseCase.class));
    context
        .withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
        .run(c -> assertThat(c).doesNotHaveBean(GetPhraseAudioUseCase.class));
  }
}
