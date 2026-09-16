package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.PersonalizationModelResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.GetPersonalizationModelUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인화 컨트롤러 — 현재는 "내 모델 상태 조회"만 구현돼 있다. 나머지는 구현체(@Service)가 없어서 여기 걸면 빈을 못 찾아 빌드가 깨진다 — 의도적으로 비워둠.
 * RecognitionController(실사용 인식 3개)는 구현된 게 하나도 없어서 아직 만들지 않았다.
 *
 * <p>TODO(백엔드 B) — 구현 완료 후 여기에 매핑 추가: - POST /api/v1/personalization/recordings →
 * UploadPersonalizationRecordingUseCase (multipart) - POST /api/v1/personalization/train →
 * TrainPersonalizationModelUseCase - GET /api/v1/personalization/train/{jobId} →
 * GetPersonalizationTrainingStatusUseCase
 */
@RestController
@RequestMapping("/api/v1/personalization")
@RequiredArgsConstructor
public class PersonalizationController {

  private final GetPersonalizationModelUseCase getPersonalizationModelUseCase;

  @GetMapping("/model")
  public ApiResponse<PersonalizationModelResponse> getModel(@AuthenticationPrincipal UUID userId) {
    var result = getPersonalizationModelUseCase.getModelStatus(userId);
    return ApiResponse.success(PersonalizationModelResponse.from(result));
  }
}
