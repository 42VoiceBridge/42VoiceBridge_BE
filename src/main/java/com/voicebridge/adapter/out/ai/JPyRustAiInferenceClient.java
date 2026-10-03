package com.voicebridge.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpyrust.JPyRustBridge;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recognition.ModelType;
import com.voicebridge.port.out.AiInferenceClient;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// AI 연동을 HTTP(HttpAiInferenceClient)로 전환하면서 기본 프로필에서 비활성화했다. JPyRust 자체 성과물은
// 보존 목적으로 코드만 남겨두며, 필요 시 이 프로필을 활성화해 다시 띄울 수 있다.
@Component
@Profile("jpyrust-experiment")
public class JPyRustAiInferenceClient implements AiInferenceClient {

  private static final int SAMPLE_RATE_HZ = 16000;
  private static final String YOLO_MODEL_PATH = "yolov8n.pt";
  private static final float YOLO_CONFIDENCE = 0.5f;

  private final JPyRustBridge bridge;
  private final ObjectMapper objectMapper;

  public JPyRustAiInferenceClient(
      ObjectMapper objectMapper,
      @Value("${voicebridge.ai.jpyrust.instance-id}") String instanceId,
      @Value("${voicebridge.ai.jpyrust.whisper-model-path}") String whisperModelPath) {
    this.objectMapper = objectMapper;
    this.bridge = new JPyRustBridge(instanceId);
    String workDir = Paths.get(System.getProperty("user.home"), ".jpyrust", instanceId).toString();
    String memoryKey = "JR" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    bridge.initialize(workDir, YOLO_MODEL_PATH, YOLO_CONFIDENCE, memoryKey, whisperModelPath);
  }

  @Override
  public RecognitionResult recognize(byte[] audioBytes, ModelType modelType, UUID userId) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(audioBytes.length);
    buffer.put(audioBytes);
    buffer.flip();

    byte[] responseBytes;
    try {
      responseBytes = bridge.processAudio(buffer, audioBytes.length, SAMPLE_RATE_HZ);
    } catch (Exception e) {
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE);
    }
    if (responseBytes == null) {
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE);
    }

    WhisperResponse response;
    try {
      response = objectMapper.readValue(responseBytes, WhisperResponse.class);
    } catch (Exception e) {
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE);
    }
    if (response.recognizedText() == null) {
      throw new CustomException(ErrorCode.AI_INFERENCE_UNAVAILABLE);
    }

    return new RecognitionResult(response.recognizedText(), response.confidence());
  }

  private record WhisperResponse(
      @JsonProperty("recognized_text") String recognizedText, double confidence) {}
}
