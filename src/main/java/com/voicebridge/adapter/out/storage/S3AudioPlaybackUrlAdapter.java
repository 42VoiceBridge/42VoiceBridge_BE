package com.voicebridge.adapter.out.storage;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.AudioPlaybackUrlPort;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@Profile("!local")
public class S3AudioPlaybackUrlAdapter implements AudioPlaybackUrlPort {
  private final S3Presigner presigner;
  private final String bucket;
  private final Duration ttl;

  public S3AudioPlaybackUrlAdapter(
      S3Presigner presigner,
      @Value("${voicebridge.s3.bucket}") String bucket,
      @Value("${voicebridge.tts.playback-url-ttl:10m}") Duration ttl) {
    if (ttl.isNegative()
        || ttl.isZero()
        || ttl.compareTo(Duration.ofDays(7)) > 0
        || ttl.getNano() != 0) {
      throw new IllegalArgumentException(
          "TTS playback URL TTL must be whole seconds between 1 second and 7 days");
    }
    this.presigner = presigner;
    this.bucket = bucket;
    this.ttl = ttl;
  }

  @Override
  public String createUrl(String storageKey, UUID ttsId) {
    return presign(storageKey);
  }

  // 문구 음성도 TTS 음성과 같은 버킷·key 형식·TTL을 쓴다. 재생 URL은 저장하지 않고 요청마다 새로 만든다.
  @Override
  public String createPhraseUrl(String storageKey, UUID phraseId) {
    return presign(storageKey);
  }

  private String presign(String storageKey) {
    TtsAudioKeys.validate(storageKey);
    try {
      return presigner
          .presignGetObject(
              GetObjectPresignRequest.builder()
                  .signatureDuration(ttl)
                  .getObjectRequest(
                      GetObjectRequest.builder()
                          .bucket(bucket)
                          .key(storageKey)
                          .responseContentType("audio/mpeg")
                          .build())
                  .build())
          .url()
          .toExternalForm();
    } catch (SdkException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "TTS 재생 URL을 생성하지 못했습니다.");
    }
  }
}
