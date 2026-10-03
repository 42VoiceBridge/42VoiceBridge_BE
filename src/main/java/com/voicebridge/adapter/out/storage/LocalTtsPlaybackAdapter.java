package com.voicebridge.adapter.out.storage;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.AudioPlaybackUrlPort;
import com.voicebridge.port.out.TtsAudioReadPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalTtsPlaybackAdapter implements AudioPlaybackUrlPort, TtsAudioReadPort {
  private final Path root;
  private final String baseUrl;

  public LocalTtsPlaybackAdapter(
      @Value("${voicebridge.storage.local-dir:./uploads}") String directory,
      @Value("${voicebridge.tts.local-playback-base-url:http://localhost:8080}") String baseUrl) {
    this.root = Path.of(directory).toAbsolutePath().normalize();
    java.net.URI uri = java.net.URI.create(baseUrl);
    if (!java.util.Set.of("http", "https").contains(uri.getScheme())
        || uri.getHost() == null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Local playback base URL must be an absolute HTTP URL");
    }
    this.baseUrl = baseUrl.replaceAll("/+$", "");
  }

  @Override
  public String createUrl(String storageKey, UUID ttsId) {
    TtsAudioKeys.validate(storageKey);
    return baseUrl + "/api/v1/tts/" + ttsId + "/audio";
  }

  @Override
  public byte[] readAudio(String storageKey) {
    TtsAudioKeys.validate(storageKey);
    try {
      Path realRoot = root.toRealPath();
      Path file = root.resolve(storageKey).toRealPath();
      if (!file.startsWith(realRoot)) {
        throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "TTS 오디오 저장 경로가 올바르지 않습니다.");
      }
      return Files.readAllBytes(file);
    } catch (java.nio.file.NoSuchFileException e) {
      throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "TTS 오디오 파일을 찾을 수 없습니다.");
    } catch (IOException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "TTS 오디오 파일을 읽지 못했습니다.");
    }
  }
}
