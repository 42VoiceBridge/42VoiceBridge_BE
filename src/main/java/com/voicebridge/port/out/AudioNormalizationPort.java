package com.voicebridge.port.out;

import java.util.Objects;

/** Converts an uploaded recording to PCM16, mono, 16 kHz WAV before inference. */
/*
 * 바이트 배열 받아서 정규화된 오디오 record로 반환한다.
 * 해당 함수에서 리턴하는 레코드는 null이 아닌 wavbyte를 받고, 해당 데이터에 대한 메타데이터를 추가로 기입한다.
 * 바이트 배열을 받을때, 리턴할때 각각 복사하는 이유는 record에 담긴 wav를 외부수정으로 부터 보호하기 위함
 * 이를 통해 AI에 전달할 바이트 + 해시가 어긋날 위험 감소 목적
 * */
public interface AudioNormalizationPort {
  NormalizedAudio normalize(byte[] source);

  record NormalizedAudio(byte[] wavBytes, Metadata metadata) {
    public NormalizedAudio {
      wavBytes = Objects.requireNonNull(wavBytes).clone();
      Objects.requireNonNull(metadata);
    }

    @Override
    public byte[] wavBytes() {
      return wavBytes.clone();
    }
  }

  record Metadata(
      String sourceFormat,
      String sourceCodec,
      int sourceSampleRate,
      int sourceChannels,
      int sampleCount,
      String sourceSha256,
      String wavSha256,
      String normalizationVersion) {}
}
