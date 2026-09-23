package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.PersonalizationModelResponse;
import com.voicebridge.adapter.in.web.dto.PersonalizationTrainingStatusResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.GetPersonalizationModelUseCase;
import com.voicebridge.port.in.GetPersonalizationTrainingStatusUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인화 컨트롤러 — 내 모델 상태 조회와 학습 작업 상태 조회를 제공한다. RecognitionController(실사용 인식 3개)는 구현된 게 하나도 없어서 아직 만들지
 * 않았다.
 *
 * <p>TODO(백엔드 B) — 구현 완료 후 여기에 매핑 추가: - POST /api/v1/personalization/recordings →
 * UploadPersonalizationRecordingUseCase (multipart) - POST /api/v1/personalization/train →
 * TrainPersonalizationModelUseCase
 */
@RestController
@RequestMapping("/api/v1/personalization")
@RequiredArgsConstructor
public class PersonalizationController {

  private final GetPersonalizationModelUseCase getPersonalizationModelUseCase;
  private final GetPersonalizationTrainingStatusUseCase getPersonalizationTrainingStatusUseCase;

  @GetMapping("/model")
  public ApiResponse<PersonalizationModelResponse> getModel(@AuthenticationPrincipal UUID userId) {
    var result = getPersonalizationModelUseCase.getModelStatus(userId);
    return ApiResponse.success(PersonalizationModelResponse.from(result));
  }

  // 학습 작업 상태를 조회하는 get 요청
  @GetMapping("/train/{jobId}")
  public ApiResponse<PersonalizationTrainingStatusResponse> getTrainingStatus(
      @AuthenticationPrincipal UUID userId, @PathVariable("jobId") UUID jobId) {

    var result = getPersonalizationTrainingStatusUseCase.getStatus(userId, jobId);

    return ApiResponse.success(PersonalizationTrainingStatusResponse.from(result));
  }
}
