package com.voicebridge.adapter.out.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.voicebridge.common.exception.CustomException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3AudioPlaybackUrlAdapterTest {
  private final String key = "recordings/" + UUID.randomUUID() + ".mp3";

  @Test
  void signsGetWithBucketKeyExpiryAndMp3ContentType() {
    try (var presigner =
        S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access", "test-secret")))
            .build()) {
      var adapter = new S3AudioPlaybackUrlAdapter(presigner, "test-bucket", Duration.ofMinutes(10));
      var url = java.net.URI.create(adapter.createUrl(key, UUID.randomUUID()));
      assertThat(url.getScheme()).isEqualTo("https");
      assertThat(url.getHost()).contains("test-bucket", "ap-northeast-2");
      assertThat(url.getPath()).isEqualTo("/" + key);
      assertThat(url.getRawQuery())
          .contains("X-Amz-Expires=600", "X-Amz-Signature=", "response-content-type=audio%2Fmpeg");
    }
  }

  @Test
  void rejectsInvalidKeyAndTtl() {
    var presigner = mock(S3Presigner.class);
    var adapter = new S3AudioPlaybackUrlAdapter(presigner, "bucket", Duration.ofMinutes(10));
    for (String bad :
        new String[] {"../file.mp3", "https://example.test/file", "personalization/file.wav"}) {
      assertThatThrownBy(() -> adapter.createUrl(bad, UUID.randomUUID()))
          .isInstanceOf(CustomException.class);
    }
    for (var ttl :
        new Duration[] {
          Duration.ZERO, Duration.ofSeconds(-1), Duration.ofDays(8), Duration.ofMillis(1)
        }) {
      assertThatThrownBy(() -> new S3AudioPlaybackUrlAdapter(presigner, "bucket", ttl))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verifyNoInteractions(presigner);
  }

  @Test
  void translatesSigningFailure() {
    var presigner = mock(S3Presigner.class);
    when(presigner.presignGetObject(
            any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
        .thenThrow(
            software.amazon.awssdk.core.exception.SdkClientException.create("no credentials"));
    assertThatThrownBy(
            () ->
                new S3AudioPlaybackUrlAdapter(presigner, "bucket", Duration.ofMinutes(10))
                    .createUrl(key, UUID.randomUUID()))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(com.voicebridge.common.exception.ErrorCode.INTERNAL_SERVER_ERROR);
  }
}
