package com.voicebridge.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.voicebridge.application.GetTtsAudioService;
import com.voicebridge.application.TtsPlaybackAccess;
import com.voicebridge.port.in.GetTtsAudioUseCase;
import com.voicebridge.port.out.TtsAudioReadPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalTtsPlaybackConfigTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(LocalTtsPlaybackConfig.class)
          .withBean(TtsPlaybackAccess.class, () -> mock(TtsPlaybackAccess.class))
          .withBean(TtsAudioReadPort.class, () -> mock(TtsAudioReadPort.class));

  @Test
  void registersAudioUseCaseOnlyInLocalProfile() {
    context
        .withInitializer(c -> c.getEnvironment().setActiveProfiles("local"))
        .run(
            c ->
                assertThat(c)
                    .hasSingleBean(GetTtsAudioUseCase.class)
                    .hasSingleBean(GetTtsAudioService.class));
    context
        .withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
        .run(c -> assertThat(c).doesNotHaveBean(GetTtsAudioUseCase.class));
  }
}
