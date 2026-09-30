package com.voicebridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.adapter.out.audio.FfmpegAudioNormalizer;
import com.voicebridge.port.out.AudioNormalizationPort;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * 스프링에서 객체 생성을 관리하고 전달하게 하는 설정 파일
 * */
@Configuration
public class AudioNormalizationConfig {
  @Bean
  AudioNormalizationPort audioNormalizationPort(
      ObjectMapper mapper,
      @Value("${voicebridge.audio.ffmpeg:ffmpeg}") String ffmpeg,
      @Value("${voicebridge.audio.ffprobe:ffprobe}") String ffprobe,
      @Value("${voicebridge.audio.temporary-directory:${java.io.tmpdir}}") Path temporaryDirectory,
      @Value("${voicebridge.audio.timeout:20s}") Duration timeout,
      @Value("${voicebridge.audio.queue-timeout:2s}") Duration queueTimeout,
      @Value("${voicebridge.audio.max-concurrent:2}") int maxConcurrent,
      @Value("${voicebridge.audio.max-source-bytes:10485760}") int maxSourceBytes,
      @Value("${voicebridge.audio.downmix-stereo:true}") boolean downmixStereo) {
    return new FfmpegAudioNormalizer(
        mapper,
        new FfmpegAudioNormalizer.Settings(
            ffmpeg,
            ffprobe,
            temporaryDirectory,
            timeout,
            queueTimeout,
            maxConcurrent,
            maxSourceBytes,
            downmixStereo));
  }
}
