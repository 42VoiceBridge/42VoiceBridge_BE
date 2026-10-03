package com.voicebridge.adapter.out.storage;

import static org.assertj.core.api.Assertions.*;

import com.voicebridge.common.exception.CustomException;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalTtsPlaybackAdapterTest {
  @TempDir Path root;

  @Test
  void readsUploadedBytesAndReturnsAuthenticatedEndpoint() throws Exception {
    var storage = new LocalFileStorageAdapter(root.toString());
    String key = storage.upload(new byte[] {1, 2, 3}, "tts.mp3");
    var adapter = new LocalTtsPlaybackAdapter(root.toString(), "http://localhost:8080/");
    UUID id = UUID.randomUUID();
    assertThat(adapter.createUrl(key, id))
        .isEqualTo("http://localhost:8080/api/v1/tts/" + id + "/audio");
    assertThat(adapter.readAudio(key)).containsExactly(1, 2, 3);
  }

  @Test
  void rejectsTraversalAndSymlinkEscape() throws Exception {
    var adapter =
        new LocalTtsPlaybackAdapter(root.resolve("uploads").toString(), "http://localhost:8080");
    assertThatThrownBy(() -> adapter.readAudio("../escape.mp3"))
        .isInstanceOf(CustomException.class);
    Path outside = root.resolve("outside.mp3");
    Files.write(outside, new byte[] {1});
    String key = "recordings/" + UUID.randomUUID() + ".mp3";
    Path link = root.resolve("uploads").resolve(key);
    Files.createDirectories(link.getParent());
    Files.createSymbolicLink(link, outside);
    assertThatThrownBy(() -> adapter.readAudio(key))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(com.voicebridge.common.exception.ErrorCode.INTERNAL_SERVER_ERROR);
  }

  @Test
  void missingFileIsNotFound() {
    assertThatThrownBy(
            () ->
                new LocalTtsPlaybackAdapter(root.toString(), "http://localhost:8080")
                    .readAudio("recordings/" + UUID.randomUUID() + ".mp3"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(com.voicebridge.common.exception.ErrorCode.RESOURCE_NOT_FOUND);
  }
}
