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

// 생성자에서 파이썬 venv를 만들고 pip install을 돌리기 때문에, 그 환경이 없는 개발 머신에서는 빈 생성이 실패하고
// 앱 전체가 기동하지 못한다. 로컬에서는 StubAiInferenceClient로 대체해 나머지 개발이 막히지 않게 한다.
@Component
@Profile("!local")
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
