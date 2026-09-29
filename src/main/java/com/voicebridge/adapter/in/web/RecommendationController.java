package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.RecommendRequest;
import com.voicebridge.adapter.in.web.dto.RecommendationResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.RecommendSentencesUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class RecommendationController {

  private final RecommendSentencesUseCase recommendSentencesUseCase;

  // 호출할 때마다 새 문장을 받아 제안한 기록을 남기므로 조회(GET)가 아니라 POST다.
  @PostMapping("/recommendations")
  public ApiResponse<RecommendationResponse> recommend(
      @AuthenticationPrincipal UUID userId,
      @RequestBody(required = false) RecommendRequest request) {
    Integer count = request == null ? null : request.count();
    return ApiResponse.success(
        RecommendationResponse.from(recommendSentencesUseCase.recommend(userId, count)));
  }
}
