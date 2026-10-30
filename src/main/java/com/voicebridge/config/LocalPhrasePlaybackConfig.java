package com.voicebridge.config;

import com.voicebridge.application.GetPhraseAudioService;
import com.voicebridge.application.PhraseAccess;
import com.voicebridge.port.out.TtsAudioReadPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalPhrasePlaybackConfig {
  @Bean
  GetPhraseAudioService getPhraseAudioService(PhraseAccess access, TtsAudioReadPort audio) {
    return new GetPhraseAudioService(access, audio);
  }
}
