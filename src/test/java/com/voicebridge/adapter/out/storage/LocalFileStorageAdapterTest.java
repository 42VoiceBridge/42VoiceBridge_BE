package com.voicebridge.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageAdapterTest {

  @TempDir Path tempDir;

  private LocalFileStorageAdapter newAdapter() {
    return new LocalFileStorageAdapter(tempDir.toString());
  }

  @Test
  void 파일을_저장하고_키를_반환한다() throws IOException {
    byte[] audio = "dummy audio bytes".getBytes();

    String key = newAdapter().upload(audio, "recording.wav");

    assertThat(key).startsWith("recordings/").endsWith(".wav");
    assertThat(Files.readAllBytes(tempDir.resolve(key))).isEqualTo(audio);
  }

  @Test
  void 허용되지_않은_확장자는_wav로_대체된다() {
    String key = newAdapter().upload(new byte[] {1}, "malware.exe");

    assertThat(key).endsWith(".wav");
  }

  @Test
  void 확장자가_없으면_wav로_대체된다() {
    String key = newAdapter().upload(new byte[] {1}, "recording");

    assertThat(key).endsWith(".wav");
  }

  @Test
  void 파일명에_경로_조작이_들어와도_키에_반영되지_않는다() {
    String key = newAdapter().upload(new byte[] {1}, "../../etc/passwd");

    assertThat(key).startsWith("recordings/").doesNotContain("..").doesNotContain("passwd");
  }

  @Test
  void 같은_파일명을_두_번_올려도_서로_다른_키가_나온다() {
    LocalFileStorageAdapter adapter = newAdapter();

    String first = adapter.upload(new byte[] {1}, "recording.wav");
    String second = adapter.upload(new byte[] {2}, "recording.wav");

    assertThat(first).isNotEqualTo(second);
  }
}
