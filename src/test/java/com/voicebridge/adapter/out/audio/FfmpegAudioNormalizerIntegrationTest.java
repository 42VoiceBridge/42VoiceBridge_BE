package com.voicebridge.adapter.out.audio;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.port.out.InvalidAudioException;
import com.voicebridge.support.AudioFixtures;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("ffmpeg")
class FfmpegAudioNormalizerIntegrationTest {
  @TempDir Path directory;
  Path work;
  FfmpegAudioNormalizer normalizer;

  @BeforeEach
  void setUp() throws Exception {
    work = Files.createDirectory(directory.resolve("work"));
    normalizer =
        new FfmpegAudioNormalizer(
            new ObjectMapper(),
            new FfmpegAudioNormalizer.Settings(
                "ffmpeg", "ffprobe", work, Duration.ofSeconds(10), 2, 10 * 1024 * 1024, true));
  }

  @Test
  void resamplesStereoAndRecordsItsProvenanceWithoutKeepingFiles() throws Exception {
    byte[] input = AudioFixtures.wav(48000, 2, 24000);
    var result = normalizer.normalize(input);
    assertThat(AudioFixtures.assertCanonicalWav(result.wavBytes())).isEqualTo(8000);
    assertThat(result.metadata().sourceChannels()).isEqualTo(2);
    assertThat(result.metadata().sourceSampleRate()).isEqualTo(48000);
    assertThat(result.metadata().normalizationVersion())
        .isEqualTo("audio-ingest-v1-stereo-average");
    assertThat(result.metadata().sourceSha256()).isEqualTo(hash(input));
    assertThat(result.metadata().wavSha256()).isEqualTo(hash(result.wavBytes()));
    assertClean();
  }

  @Test
  void cleanupFailureDoesNotDiscardSuccessfulConversion() throws Exception {
    Path wrapper = directory.resolve("ffmpeg-with-leftover");
    Files.writeString(
        wrapper,
        "#!/bin/sh\n"
            + "output=''\n"
            + "for arg in \"$@\"; do output=\"$arg\"; done\n"
            + "ffmpeg \"$@\" || exit $?\n"
            + "touch \"$(dirname \"$output\")/leftover\"\n");
    assertThat(wrapper.toFile().setExecutable(true)).isTrue();
    var withLeftover =
        new FfmpegAudioNormalizer(
            new ObjectMapper(),
            new FfmpegAudioNormalizer.Settings(
                wrapper.toString(),
                "ffprobe",
                work,
                Duration.ofSeconds(10),
                2,
                10 * 1024 * 1024,
                true));

    var result = withLeftover.normalize(AudioFixtures.wav(16000, 1, 8000));
    assertThat(AudioFixtures.assertCanonicalWav(result.wavBytes())).isEqualTo(8000);

    Path workspace;
    try (var entries = Files.list(work)) {
      var directories = entries.toList();
      assertThat(directories).hasSize(1);
      workspace = directories.get(0);
    }
    assertThat(workspace.resolve("leftover")).exists();
    assertThat(workspace.resolve("input")).doesNotExist();
    assertThat(workspace.resolve("decoded.f32")).doesNotExist();
    Files.delete(workspace.resolve("leftover"));
    Files.delete(workspace);
    assertClean();
  }

  @Test
  void stereoMixIsTheExplicitAverageOfBothChannels() {
    byte[] input = AudioFixtures.wav(16000, 2, 8000);
    ByteBuffer source = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < 8000; i++) {
      source.putShort(44 + i * 4, (short) 2000);
      source.putShort(46 + i * 4, (short) 6000);
    }
    ByteBuffer output =
        ByteBuffer.wrap(normalizer.normalize(input).wavBytes()).order(ByteOrder.LITTLE_ENDIAN);
    assertThat(output.getShort(44 + 2000)).isEqualTo((short) 4000);
  }

  @ParameterizedTest
  @CsvSource({"webm,libopus", "m4a,aac"})
  void decodesPhoneContainers(String extension, String codec) throws Exception {
    byte[] input = AudioFixtures.encoded(directory, extension, codec, 2);
    var result = normalizer.normalize(input);
    assertThat(AudioFixtures.assertCanonicalWav(result.wavBytes())).isBetween(7800, 8500);
    assertThat(result.metadata().sourceChannels()).isEqualTo(2);
    assertClean();
  }

  @ParameterizedTest
  @ValueSource(ints = {4800, 480000})
  void acceptsBothDurationBoundaries(int samples) {
    var result = normalizer.normalize(AudioFixtures.wav(16000, 1, samples));
    assertThat(AudioFixtures.assertCanonicalWav(result.wavBytes())).isEqualTo(samples);
    assertClean();
  }

  @ParameterizedTest
  @CsvSource({"4799, TOO_SHORT", "480001, TOO_LONG", "640000, TOO_LONG"})
  void rejectsOutOfRangeDurationRatherThanReturningTruncatedAudio(
      int samples, InvalidAudioException.Reason expectedReason) {
    assertThatThrownBy(() -> normalizer.normalize(AudioFixtures.wav(16000, 1, samples)))
        .isInstanceOf(InvalidAudioException.class)
        .satisfies(
            failure ->
                assertThat(((InvalidAudioException) failure).reason()).isEqualTo(expectedReason));
    assertClean();
  }

  @Test
  void silenceRemainsAValidInput() {
    byte[] input = AudioFixtures.wav(16000, 1, 8000);
    Arrays.fill(input, 44, input.length, (byte) 0);
    assertThat(normalizer.normalize(input).wavBytes()).isEqualTo(input);
  }

  @Test
  void malformedAndTruncatedInputsAreRejectedAndCleanedUp() {
    byte[] valid = AudioFixtures.wav(16000, 1, 8000);
    for (byte[] input : new byte[][] {{1, 2, 3}, Arrays.copyOf(valid, valid.length - 2000)}) {
      assertThatThrownBy(() -> normalizer.normalize(input))
          .isInstanceOf(InvalidAudioException.class);
      assertClean();
    }
  }

  @Test
  void rejectsNonFiniteFloatSamplesBeforePcmQuantization() {
    byte[] source = AudioFixtures.wav(16000, 2, 8000);
    ByteBuffer in = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
    in.putShort(20, (short) 3).putShort(22, (short) 1).putShort(34, (short) 32);
    for (int offset = 44; offset < source.length; offset += 4) in.putFloat(offset, Float.NaN);
    assertThatThrownBy(() -> normalizer.normalize(source))
        .isInstanceOf(InvalidAudioException.class);
    assertClean();
  }

  @Test
  void unsupportedChannelsAndRawFormatsCannotSlipThrough() throws Exception {
    assertThatThrownBy(() -> normalizer.normalize(AudioFixtures.wav(16000, 3, 8000)))
        .isInstanceOf(InvalidAudioException.class);
    byte[] mp3 = AudioFixtures.encoded(directory, "mp3", "libmp3lame", 1);
    assertThatThrownBy(() -> normalizer.normalize(mp3)).isInstanceOf(InvalidAudioException.class);
    assertClean();
  }

  private String hash(byte[] value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private void assertClean() {
    assertThat(work).isEmptyDirectory();
  }
}
