package com.voicebridge.adapter.out.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3StorageAdapterTest {
  @Test
  void uploadReturnsKeyAndSetsMp3ContentType() {
    var s3 = mock(S3Client.class);
    String key = new S3StorageAdapter(s3, "bucket").upload(new byte[] {1, 2}, "tts.mp3");
    var captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(captor.capture(), any(RequestBody.class));
    assertThat(key).startsWith("recordings/").endsWith(".mp3");
    assertThat(captor.getValue().key()).isEqualTo(key);
    assertThat(captor.getValue().contentType()).isEqualTo("audio/mpeg");
  }
}
