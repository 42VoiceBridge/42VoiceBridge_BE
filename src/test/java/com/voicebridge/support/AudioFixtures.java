package com.voicebridge.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class AudioFixtures {
  private AudioFixtures() {}

  public static byte[] wav(int sampleRate, int channels, int frames) {
    int size = frames * channels * 2;
    ByteBuffer out = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN);
    out.put("RIFF".getBytes(StandardCharsets.US_ASCII))
        .putInt(36 + size)
        .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) channels)
        .putInt(sampleRate)
        .putInt(sampleRate * channels * 2)
        .putShort((short) (channels * 2))
        .putShort((short) 16)
        .put("data".getBytes(StandardCharsets.US_ASCII))
        .putInt(size);
    for (int i = 0; i < frames; i++) {
      for (int channel = 0; channel < channels; channel++) {
        out.putShort((short) (3000 * (channel + 1) * Math.sin(2 * Math.PI * 440 * i / sampleRate)));
      }
    }
    return out.array();
  }

  public static byte[] encoded(Path directory, String extension, String codec, int channels)
      throws Exception {
    Path input = directory.resolve("fixture.wav");
    Path output = directory.resolve("fixture." + extension);
    Files.write(input, wav(48000, channels, 24000));
    Process process =
        new ProcessBuilder(
                List.of(
                    "ffmpeg",
                    "-nostdin",
                    "-v",
                    "error",
                    "-y",
                    "-i",
                    input.toString(),
                    "-c:a",
                    codec,
                    output.toString()))
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    try {
      assertThat(process.waitFor(15, TimeUnit.SECONDS))
          .as("ffmpeg fixture encoder finishes")
          .isTrue();
      assertThat(process.exitValue()).isZero();
      return Files.readAllBytes(output);
    } finally {
      process.destroyForcibly();
    }
  }

  public static int assertCanonicalWav(byte[] wav) {
    ByteBuffer in = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
    assertThat(new String(wav, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
    assertThat(in.getInt(4)).isEqualTo(wav.length - 8);
    assertThat(new String(wav, 8, 8, StandardCharsets.US_ASCII)).isEqualTo("WAVEfmt ");
    assertThat(in.getShort(20)).isEqualTo((short) 1);
    assertThat(in.getShort(22)).isEqualTo((short) 1);
    assertThat(in.getInt(24)).isEqualTo(16000);
    assertThat(in.getInt(28)).isEqualTo(32000);
    assertThat(in.getShort(32)).isEqualTo((short) 2);
    assertThat(in.getShort(34)).isEqualTo((short) 16);
    assertThat(new String(wav, 36, 4, StandardCharsets.US_ASCII)).isEqualTo("data");
    assertThat(in.getInt(40)).isEqualTo(wav.length - 44);
    return in.getInt(40) / 2;
  }
}
