package com.voicebridge.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.adapter.in.web.dto.PersonalizationModelResponse;
import com.voicebridge.adapter.in.web.dto.PersonalizationTrainingStatusResponse;
import com.voicebridge.adapter.in.web.dto.TrainPersonalizationResponse;
import com.voicebridge.adapter.in.web.dto.UploadPersonalizationRecordingResponse;
import com.voicebridge.application.DeletePersonalizationRecordingService;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.common.response.ApiResponse;
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
  private final DeletePersonalizationRecordingService deleteRecording;
  private final UploadPersonalizationRecordingUseCase uploadRecording;
  private final TrainPersonalizationModelUseCase trainModel;
  private final GetPersonalizationModelUseCase getPersonalizationModelUseCase;
  private final GetPersonalizationTrainingStatusUseCase getPersonalizationTrainingStatusUseCase;

  @PostMapping(value = "/recordings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<UploadPersonalizationRecordingResponse>> upload(
      @AuthenticationPrincipal UUID userId,
      @RequestPart("metadata") MultipartFile metadata,
      @RequestPart("audioFile") MultipartFile audioFile) {
    if (!MediaType.APPLICATION_JSON_VALUE.equals(metadata.getContentType())
        || metadata.isEmpty()
        || metadata.getSize() > 4096) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    JsonNode node;
    try {
      node = objectMapper.readTree(metadata.getBytes());
    } catch (IOException e) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    if (node == null
        || !node.isObject()
        || (node.has("storeAudio") && !node.get("storeAudio").isBoolean())
        || (node.has("useForTraining") && !node.get("useForTraining").isBoolean())) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    UUID shownPromptId;
    try {
      shownPromptId = UUID.fromString(node.path("shownPromptId").asText());
    } catch (IllegalArgumentException e) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    String promptId = node.path("promptId").asText(null);
    if (audioFile.isEmpty()) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
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
