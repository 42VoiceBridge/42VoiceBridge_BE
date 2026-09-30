package com.voicebridge.adapter.out.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.port.out.AudioNormalizationPort;
import com.voicebridge.port.out.AudioProcessingException;
import com.voicebridge.port.out.InvalidAudioException;
import com.voicebridge.port.out.InvalidAudioException.Reason;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** Decoding and process details stay outside the application and inference transport. */
/** 해당 구현체의 목표는 브라우저가 보낸 다양한 포맷의 음성을 AI가 받을 수 있는 한가지 규격으로 정규화 시키는 것 */
@Slf4j
public final class FfmpegAudioNormalizer implements AudioNormalizationPort {
  private static final int SAMPLE_RATE = 16000;
  private static final int MIN_SAMPLES = 4800;
  private static final int MAX_SAMPLES = 480000;
  private static final int MAX_DECODED_BYTES = 31 * SAMPLE_RATE * Float.BYTES;
  private static final Set<String> PCM_CODECS =
      Set.of("pcm_u8", "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_f32le", "pcm_f64le");

  private final ObjectMapper mapper;
  private final Settings settings;
  private final Semaphore permits;

  // FFMpeg 경로, 임시 디렉토리, 기타 작업 제한등의 파라미터를 받음 - 실행환경에 따라 달라지는 값을 변환 로직으로부터 분리하기 위함
  public record Settings(
      String ffmpeg,
      String ffprobe,
      Path temporaryDirectory,
      Duration timeout,
      int maxConcurrent,
      int maxSourceBytes,
      boolean downmixStereo) {
    public Settings {
      if (ffmpeg == null
          || ffmpeg.isBlank()
          || ffprobe == null
          || ffprobe.isBlank()
          || temporaryDirectory == null
          || timeout == null
          || timeout.isNegative()
          || timeout.isZero()
          || timeout.compareTo(Duration.ofMinutes(5)) > 0
          || maxConcurrent < 1
          || maxSourceBytes < 1) {
        throw new IllegalArgumentException("Invalid audio normalization settings");
      }
    }
  }

  // 생성자, 설정과 json 해석 도구 받아서 보관하고, 세마포어로 동시 변환 가능한 요청수를 제한한다.
  // ObjectMapper는 ffprobe가 출력한 JSON을 JsonNode로 읽는 Jackson 객체다.
  public FfmpegAudioNormalizer(ObjectMapper mapper, Settings settings) {
    this.mapper = mapper;
    this.settings = settings;
    this.permits = new Semaphore(settings.maxConcurrent());
  }

  // 입력 검사 - 변환 - 결과 생성 및 정리를 한 요청 단위로 묶어줌
  @Override
  public NormalizedAudio normalize(byte[] source) {
    // 입력 파라미터 유효성 검사
    if (source == null || source.length == 0) {
      throw new InvalidAudioException(Reason.INVALID, "Audio is empty");
    }
    if (source.length > settings.maxSourceBytes()) {
      throw new InvalidAudioException(Reason.INVALID, "Audio upload exceeds the size limit");
    }
    if (!hasSupportedContainerSignature(source)) {
      throw new InvalidAudioException(Reason.INVALID, "Unsupported or damaged audio container");
    }
    if (!permits.tryAcquire()) {
      throw new AudioProcessingException("Audio conversion capacity exhausted");
    }
    long deadline = System.nanoTime() + settings.timeout().toNanos();
    // 이번 요청 전용 임시 디렉터리와 파일 경로 묶음
    // Workspace는 요청 전용 임시 디렉터리를 만들고 파일 경로를 정한다. 블록 종료 시 임시 파일을 정리한다.
    try (Workspace workspace = new Workspace(settings.temporaryDirectory())) {
      Files.write(workspace.input, source);
      run(
          List.of(
              settings.ffprobe(),
              "-v",
              "error",
              "-protocol_whitelist",
              "file",
              "-format_whitelist",
              "wav,matroska,mov",
              "-show_entries",
              "format=format_name:stream=codec_type,codec_name,sample_rate,channels",
              "-of",
              "json",
              workspace.input.toString()),
          workspace.probe,
          workspace.processError,
          65536,
          deadline);
      SourceInfo info = inspect(mapper.readTree(Files.readAllBytes(workspace.probe)));
      List<String> command =
          new ArrayList<>(
              List.of(
                  settings.ffmpeg(),
                  "-nostdin",
                  "-hide_banner",
                  "-v",
                  "error",
                  "-xerror",
                  "-protocol_whitelist",
                  "file",
                  "-format_whitelist",
                  "wav,matroska,mov",
                  "-threads",
                  "1",
                  "-i",
                  workspace.input.toString(),
                  "-map",
                  "0:a:0",
                  "-vn",
                  "-sn",
                  "-dn",
                  "-t",
                  "31",
                  "-ac",
                  "1",
                  "-ar",
                  "16000",
                  "-threads",
                  "1",
                  "-f",
                  "f32le",
                  "-c:a",
                  "pcm_f32le",
                  "-y",
                  workspace.decoded.toString()));
      if (info.channels() == 2) {
        command.addAll(command.size() - 1, List.of("-af", "pan=mono|c0=0.5*c0+0.5*c1"));
      }
      // A 31-second decode ceiling bounds work; anything above 30 seconds is rejected below.
      run(command, workspace.processOutput, workspace.processError, 1024, deadline);
      long length = Files.size(workspace.decoded);
      if (length > MAX_DECODED_BYTES || length % Float.BYTES != 0) {
        throw new InvalidAudioException(Reason.INVALID, "Invalid decoded audio length");
      }
      byte[] wav = encodeWav(Files.readAllBytes(workspace.decoded));
      Metadata metadata =
          new Metadata(
              info.format(),
              info.codec(),
              info.sampleRate(),
              info.channels(),
              (wav.length - 44) / 2,
              sha256(source),
              sha256(wav),
              info.channels() == 2 ? "audio-ingest-v1-stereo-average" : "audio-ingest-v1-mono");
      log.info(
          "Audio normalized: format={}, codec={}, channels={}, samples={}, version={}",
          metadata.sourceFormat(),
          metadata.sourceCodec(),
          metadata.sourceChannels(),
          metadata.sampleCount(),
          metadata.normalizationVersion());
      return new NormalizedAudio(wav, metadata);
    } catch (IOException e) {
      throw new AudioProcessingException("Audio processing I/O failed", e);
    } finally {
      permits.release();
    }
  }

  // ffprobe가 읽은 실제 형식 / 코덱 / 채널 수를 허용 정책과 비교
  private SourceInfo inspect(JsonNode root) {
    JsonNode streams = root.path("streams");
    if (!streams.isArray()
        || streams.size() != 1
        || !"audio".equals(streams.get(0).path("codec_type").asText())) {
      throw new InvalidAudioException(Reason.INVALID, "Exactly one audio stream is required");
    }
    JsonNode stream = streams.get(0);
    String format = root.path("format").path("format_name").asText();
    String codec = stream.path("codec_name").asText();
    boolean supported =
        (format.equals("wav") && PCM_CODECS.contains(codec))
            || (format.equals("matroska,webm") && Set.of("opus", "vorbis").contains(codec))
            || (format.equals("mov,mp4,m4a,3gp,3g2,mj2") && codec.equals("aac"));
    if (!supported) {
      throw new InvalidAudioException(
          Reason.INVALID, "Supported audio: PCM WAV, WebM Opus/Vorbis, M4A AAC");
    }
    int channels = stream.path("channels").asInt();
    if (channels != 1 && !(channels == 2 && settings.downmixStereo())) {
      throw new InvalidAudioException(Reason.INVALID, "Unsupported audio channel count");
    }
    int sampleRate = stream.path("sample_rate").asInt();
    if (sampleRate < 1 || sampleRate > 192000) {
      throw new InvalidAudioException(Reason.INVALID, "Unsupported audio sample rate");
    }
    return new SourceInfo(format, codec, sampleRate, channels);
  }

  private record SourceInfo(String format, String codec, int sampleRate, int channels) {}

  // 파일 앞부분의 형식 식별 바이트 검사로직 - 이 함수만으로 정상 음성임을 확정할 수는 없음
  private static boolean hasSupportedContainerSignature(byte[] source) {
    if (source.length < 12) return false;
    return (source[0] == 'R'
            && source[1] == 'I'
            && source[2] == 'F'
            && source[3] == 'F'
            && source[8] == 'W'
            && source[9] == 'A'
            && source[10] == 'V'
            && source[11] == 'E')
        || ((source[0] & 0xff) == 0x1a
            && (source[1] & 0xff) == 0x45
            && (source[2] & 0xff) == 0xdf
            && (source[3] & 0xff) == 0xa3)
        || (source[4] == 'f' && source[5] == 't' && source[6] == 'y' && source[7] == 'p');
  }

  // 디코딩된 샘플의 길이 및 유효성을 검사하고 최종 PCM16 WAV만듬
  private byte[] encodeWav(byte[] decoded) {
    int samples = decoded.length / Float.BYTES;
    if (samples < MIN_SAMPLES) {
      throw new InvalidAudioException(Reason.TOO_SHORT, "Audio is shorter than 0.3 seconds");
    }
    if (samples > MAX_SAMPLES) {
      throw new InvalidAudioException(Reason.TOO_LONG, "Audio is longer than 30 seconds");
    }
    int size = samples * Short.BYTES;
    ByteBuffer wav = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN);
    wav.put("RIFF".getBytes(StandardCharsets.US_ASCII))
        .putInt(36 + size)
        .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1)
        .putInt(SAMPLE_RATE)
        .putInt(SAMPLE_RATE * 2)
        .putShort((short) 2)
        .putShort((short) 16)
        .put("data".getBytes(StandardCharsets.US_ASCII))
        .putInt(size);
    ByteBuffer floats = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN);
    while (floats.hasRemaining()) {
      float sample = floats.getFloat();
      if (!Float.isFinite(sample)) {
        throw new InvalidAudioException(Reason.INVALID, "Audio contains non-finite samples");
      }
      int value = Math.round(Math.max(-1f, Math.min(1f, sample)) * 32768f);
      wav.putShort((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value)));
    }
    return wav.array();
  }

  // ffprobe와 ffmpeg 프로세스를 실행하고 시간·출력 크기를 감시 - 호출하는 쪽에서 실행 파일 및 옵션 조립해서 run에 전달
  private void run(List<String> command, Path output, Path error, long maxOutput, long deadline)
      throws IOException {
    if (System.nanoTime() >= deadline) {
      throw new AudioProcessingException("Audio processing deadline exceeded");
    }
    Process process =
        new ProcessBuilder(command)
            .redirectOutput(output.toFile())
            .redirectError(error.toFile())
            .start();
    try {
      process.getOutputStream().close();
      while (!process.waitFor(50, TimeUnit.MILLISECONDS)) {
        if (System.nanoTime() >= deadline) {
          throw new AudioProcessingException("Audio processing deadline exceeded");
        }
        if (Files.size(output) > maxOutput) {
          throw new InvalidAudioException(
              Reason.INVALID, "Audio metadata exceeds the processing limit");
        }
        if (Files.size(error) > 8192) {
          throw new AudioProcessingException("Audio converter diagnostics exceeded the limit");
        }
      }
      if (Files.size(output) > maxOutput) {
        throw new InvalidAudioException(
            Reason.INVALID, "Audio metadata exceeds the processing limit");
      }
      if (Files.size(error) > 8192) {
        throw new AudioProcessingException("Audio converter diagnostics exceeded the limit");
      }
      if (System.nanoTime() >= deadline) {
        throw new AudioProcessingException("Audio processing deadline exceeded");
      }
      if (process.exitValue() != 0) {
        String diagnostic = Files.readString(error, StandardCharsets.UTF_8);
        log.warn(
            "Audio conversion process failed: exit={}, diagnostic={}",
            process.exitValue(),
            diagnostic);
        if (isKnownInputFailure(diagnostic)) {
          throw new InvalidAudioException(Reason.INVALID, "Audio could not be decoded");
        }
        throw new AudioProcessingException("Audio converter failed; check server diagnostics");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AudioProcessingException("Audio processing interrupted", e);
    } finally {
      if (process.isAlive()) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        // Reap the child before the workspace is deleted, including interrupted calls.
        process.onExit().join();
      }
    }
  }

  private static boolean isKnownInputFailure(String diagnostic) {
    return diagnostic.contains("Invalid data found when processing input")
        || diagnostic.contains("moov atom not found")
        || diagnostic.contains("End of file");
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("SHA-256 is required by the Java runtime", e);
    }
  }

  private static final class Workspace implements AutoCloseable {
    private final Path directory;
    private final Path input;
    private final Path probe;
    private final Path decoded;
    private final Path processOutput;
    private final Path processError;

    private Workspace(Path parent) throws IOException {
      directory = Files.createTempDirectory(parent, "voicebridge-audio-");
      input = directory.resolve("input");
      probe = directory.resolve("probe.json");
      decoded = directory.resolve("decoded.f32");
      processOutput = directory.resolve("stdout");
      processError = directory.resolve("stderr");
    }

    @Override
    public void close() {
      IOException failure = null;
      for (Path path : List.of(input, probe, decoded, processOutput, processError, directory)) {
        try {
          Files.deleteIfExists(path);
        } catch (IOException e) {
          if (failure == null) failure = e;
          else failure.addSuppressed(e);
        }
      }
      if (failure != null) {
        log.warn("Audio temporary workspace cleanup failed: directory={}", directory, failure);
      }
    }
  }
}
