package com.voicebridge.config;

import com.voicebridge.application.GetTtsAudioService;
import com.voicebridge.application.TtsPlaybackAccess;
import com.voicebridge.port.out.TtsAudioReadPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalTtsPlaybackConfig {
  @Bean
  GetTtsAudioService getTtsAudioService(TtsPlaybackAccess access, TtsAudioReadPort audio) {
    return new GetTtsAudioService(access, audio);
  }
}
