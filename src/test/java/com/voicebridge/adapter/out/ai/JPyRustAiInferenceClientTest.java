package com.voicebridge.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JPyRustAiInferenceClientTest {

  private static final Logger log = LoggerFactory.getLogger(JPyRustAiInferenceClientTest.class);

  private final JPyRustAiInferenceClient client =
      new JPyRustAiInferenceClient(new ObjectMapper(), "voicebridge-test", "tiny");

  @Test
  void 더미_오디오로_processAudio_왕복이_성공한다() {
    byte[] wavBytes = buildDummyWav(16000, 1.0);

    AiInferenceClient.RecognitionResult result;
    try {
      result = client.recognize(wavBytes, ModelType.BASE_ADAPTED, UUID.randomUUID());
    } catch (CustomException e) {
      log.warn("Whisper 모델을 사용할 수 없어 테스트를 건너뜁니다: {}", e.getMessage());
      assumeTrue(false, "Whisper 모델 로드 실패로 스킵: " + e.getMessage());
      throw e;
    }

    assertThat(result.recognizedText()).isNotNull();
    assertThat(result.confidence()).isBetween(0.0, 1.0);
  }

  private static byte[] buildDummyWav(int sampleRate, double seconds) {
    try {
      int numSamples = (int) (sampleRate * seconds);
      ByteArrayOutputStream pcm = new ByteArrayOutputStream();
      DataOutputStream pcmOut = new DataOutputStream(pcm);
      for (int i = 0; i < numSamples; i++) {
        double t = (double) i / sampleRate;
        short sample = (short) (Math.sin(2 * Math.PI * 440.0 * t) * 3000);
        pcmOut.writeByte(sample & 0xFF);
        pcmOut.writeByte((sample >> 8) & 0xFF);
      }
      byte[] pcmBytes = pcm.toByteArray();

      ByteArrayOutputStream wav = new ByteArrayOutputStream();
      int byteRate = sampleRate * 2;
      int dataSize = pcmBytes.length;
      wav.write("RIFF".getBytes("US-ASCII"));
      wav.write(intToBytesLE(36 + dataSize));
      wav.write("WAVE".getBytes("US-ASCII"));
      wav.write("fmt ".getBytes("US-ASCII"));
      wav.write(intToBytesLE(16));
      wav.write(shortToBytesLE((short) 1));
      wav.write(shortToBytesLE((short) 1));
      wav.write(intToBytesLE(sampleRate));
      wav.write(intToBytesLE(byteRate));
      wav.write(shortToBytesLE((short) 2));
      wav.write(shortToBytesLE((short) 16));
      wav.write("data".getBytes("US-ASCII"));
      wav.write(intToBytesLE(dataSize));
      wav.write(pcmBytes);
      return wav.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] intToBytesLE(int v) {
    return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
  }

  private static byte[] shortToBytesLE(short v) {
    return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array();
  }
}
