package com.voicebridge.adapter.out.audio;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.port.out.AudioProcessingException;
import com.voicebridge.port.out.InvalidAudioException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioProcessLifecycleTest {
  @TempDir Path directory;

  @Test
  void missingExecutableIsInfrastructureFailureAndPermitIsReleased() throws Exception {
    Path work = Files.createDirectory(directory.resolve("work"));
    var normalizer = normalizer(work, "missing-voicebridge-ffprobe", Duration.ofSeconds(2));
    for (int i = 0; i < 2; i++) {
      assertThatThrownBy(() -> normalizer.normalize(probeInput()))
          .isInstanceOf(AudioProcessingException.class)
          .hasMessageContaining("I/O");
      assertThat(work).isEmptyDirectory();
    }
  }

  @Test
  void killsTimedOutProcessCleansWorkspaceAndRefusesConcurrentWork() throws Exception {
    Path work = Files.createDirectory(directory.resolve("work"));
    Path pidFile = directory.resolve("pid");
    Path script = directory.resolve("probe");
    Files.writeString(script, "#!/bin/sh\necho $$ > '" + pidFile + "'\nexec sleep 30\n");
    assertThat(script.toFile().setExecutable(true)).isTrue();
    var normalizer = normalizer(work, script.toString(), Duration.ofSeconds(2));
    var executor = Executors.newSingleThreadExecutor();
    try {
      var first = executor.submit(() -> normalizer.normalize(probeInput()));
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
      while (!Files.exists(pidFile) && System.nanoTime() < deadline) Thread.sleep(10);
      assertThat(pidFile).exists();
      long pid = Long.parseLong(Files.readString(pidFile).trim());
      assertThatThrownBy(() -> normalizer.normalize(probeInput()))
          .isInstanceOf(AudioProcessingException.class)
          .hasMessageContaining("capacity");
      assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS))
          .hasCauseInstanceOf(AudioProcessingException.class);
      assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
      assertThat(work).isEmptyDirectory();
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void metadataOutputIsBoundedAndUploadLimitsApplyBeforeStartingTools() throws Exception {
    Path work = Files.createDirectory(directory.resolve("work"));
    Path script = directory.resolve("probe");
    Files.writeString(script, "#!/bin/sh\ndd if=/dev/zero bs=70000 count=1 2>/dev/null\n");
    assertThat(script.toFile().setExecutable(true)).isTrue();
    var normalizer = normalizer(work, script.toString(), Duration.ofSeconds(2));
    assertThatThrownBy(() -> normalizer.normalize(probeInput()))
        .isInstanceOf(InvalidAudioException.class);
    assertThat(work).isEmptyDirectory();
    assertThatThrownBy(() -> normalizer.normalize(new byte[1025]))
        .isInstanceOf(InvalidAudioException.class);
    assertThat(work).isEmptyDirectory();
  }

  @Test
  void converterConfigurationFailureIsNotReportedAsBadUserAudio() throws Exception {
    Path work = Files.createDirectory(directory.resolve("work"));
    Path script = directory.resolve("probe");
    Files.writeString(script, "#!/bin/sh\necho 'Unrecognized option: fake' >&2\nexit 1\n");
    assertThat(script.toFile().setExecutable(true)).isTrue();
    var normalizer = normalizer(work, script.toString(), Duration.ofSeconds(2));
    assertThatThrownBy(() -> normalizer.normalize(probeInput()))
        .isInstanceOf(AudioProcessingException.class);
    assertThat(work).isEmptyDirectory();
  }

  private byte[] probeInput() {
    return new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
  }

  private FfmpegAudioNormalizer normalizer(Path work, String probe, Duration timeout) {
    return new FfmpegAudioNormalizer(
        new ObjectMapper(),
        new FfmpegAudioNormalizer.Settings("unused-ffmpeg", probe, work, timeout, 1, 1024, true));
  }
}
