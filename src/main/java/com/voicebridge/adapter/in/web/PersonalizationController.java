package com.voicebridge.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.adapter.in.web.dto.PersonalizationModelResponse;
import com.voicebridge.adapter.in.web.dto.PersonalizationTrainingStatusResponse;
import com.voicebridge.adapter.in.web.dto.TrainPersonalizationResponse;
import com.voicebridge.adapter.in.web.dto.UploadPersonalizationRecordingResponse;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.DeletePersonalizationRecordingUseCase;
import com.voicebridge.port.in.GetPersonalizationModelUseCase;
import com.voicebridge.port.in.GetPersonalizationTrainingStatusUseCase;
import com.voicebridge.port.in.TrainPersonalizationModelUseCase;
import com.voicebridge.port.in.UploadPersonalizationRecordingUseCase;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/personalization")
@RequiredArgsConstructor
public class PersonalizationController {

  private final ObjectMapper objectMapper;
  private final DeletePersonalizationRecordingUseCase deleteRecording;
  private final UploadPersonalizationRecordingUseCase uploadRecording;
  private final TrainPersonalizationModelUseCase trainModel;
  private final GetPersonalizationModelUseCase getPersonalizationModelUseCase;
  private final GetPersonalizationTrainingStatusUseCase getPersonalizationTrainingStatusUseCase;

  @PostMapping(value = "/recordings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<UploadPersonalizationRecordingResponse>> upload(
      @AuthenticationPrincipal UUID userId,
      @RequestPart("metadata") MultipartFile metadata,
      @RequestPart("audioFile") MultipartFile audioFile) {
    try {
      if (metadata.getContentType() == null
          || !MediaType.parseMediaType(metadata.getContentType())
              .isCompatibleWith(MediaType.APPLICATION_JSON)) {
        throw invalid("metadata의 Content-Type은 application/json이어야 합니다.");
      }
    } catch (org.springframework.http.InvalidMediaTypeException e) {
      throw invalid("metadata의 Content-Type은 application/json이어야 합니다.");
    }
    if (metadata.isEmpty()) {
      throw invalid("metadata가 비어 있습니다.");
    }
    if (metadata.getSize() > 4096) {
      throw invalid("metadata는 4096바이트 이하여야 합니다.");
    }
    JsonNode node;
    try {
      node =
          objectMapper
              .reader()
              .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
              .readTree(metadata.getBytes());
    } catch (IOException e) {
      throw invalid("metadata는 올바른 JSON 객체여야 합니다.");
    }
    if (node == null || !node.isObject()) {
      throw invalid("metadata는 올바른 JSON 객체여야 합니다.");
    }
    for (String field : new String[] {"storeAudio", "useForTraining"}) {
      if (node.has(field) && !node.get(field).isBoolean()) {
        throw invalid(field + "는 boolean 값이어야 합니다.");
      }
    }
    UUID shownPromptId;
    try {
      JsonNode id = node.path("shownPromptId");
      if (!id.isTextual()) {
        throw new IllegalArgumentException();
      }
      shownPromptId = UUID.fromString(id.textValue());
      if (!shownPromptId.toString().equalsIgnoreCase(id.textValue())) {
        throw new IllegalArgumentException();
      }
    } catch (IllegalArgumentException e) {
      throw invalid("shownPromptId는 유효한 UUID 문자열이어야 합니다.");
    }
    JsonNode prompt = node.path("promptId");
    if (!prompt.isTextual() || prompt.textValue().isBlank()) {
      throw invalid("promptId는 비어 있지 않은 문자열이어야 합니다.");
    }
    String promptId = prompt.textValue();
    if (audioFile.isEmpty()) {
      throw invalid("audioFile이 비어 있습니다.");
    }
    byte[] bytes;
    try {
      bytes = audioFile.getBytes();
    } catch (IOException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
    var result =
        uploadRecording.upload(
            new UploadPersonalizationRecordingUseCase.UploadCommand(
                userId,
                shownPromptId,
                promptId,
                node.path("storeAudio").asBoolean(false),
                node.path("useForTraining").asBoolean(false),
                bytes));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                new UploadPersonalizationRecordingResponse(result.recordingId(), result.status())));
  }

  private static CustomException invalid(String message) {
    return new CustomException(ErrorCode.VALIDATION_FAILED, message);
  }

  @DeleteMapping("/recordings/{recordingId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal UUID userId, @PathVariable("recordingId") UUID recordingId) {
    deleteRecording.delete(userId, recordingId);
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/train")
  public ResponseEntity<ApiResponse<TrainPersonalizationResponse>> train(
      @AuthenticationPrincipal UUID userId) {
    var result = trainModel.train(userId);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(new TrainPersonalizationResponse(result.jobId(), result.status())));
  }

  @GetMapping("/model")
  public ApiResponse<PersonalizationModelResponse> getModel(@AuthenticationPrincipal UUID userId) {
    var result = getPersonalizationModelUseCase.getModelStatus(userId);
    return ApiResponse.success(PersonalizationModelResponse.from(result));
  }

  // 학습 작업 상태를 조회하는 get 요청
  @GetMapping("/train/{jobId}")
  public ApiResponse<PersonalizationTrainingStatusResponse> getTrainingStatus(
      @AuthenticationPrincipal UUID userId, @PathVariable("jobId") UUID jobId) {
    // @AuthenticationPrincipal UUID userId - 인증 과정에서 확인된 로그인 사용자 id
    //  @PathVariable("jobId") UUID jobId - get 요청 url 경로상의 {jobId}를 받음
    var result = getPersonalizationTrainingStatusUseCase.getStatus(userId, jobId);
    return ApiResponse.success(PersonalizationTrainingStatusResponse.from(result));
    // result를 인자로 받아서 json 응답 생성 및 전송
  }
}
