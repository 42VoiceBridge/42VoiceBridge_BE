package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.JamoErrorStatsResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.GetJamoErrorStatsUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class JamoErrorStatsController {

  private final GetJamoErrorStatsUseCase getJamoErrorStatsUseCase;

  @GetMapping("/jamo-error-stats")
  public ApiResponse<JamoErrorStatsResponse> getStats(@AuthenticationPrincipal UUID userId) {
    return ApiResponse.success(
        JamoErrorStatsResponse.from(getJamoErrorStatsUseCase.getStats(userId)));
  }
}
