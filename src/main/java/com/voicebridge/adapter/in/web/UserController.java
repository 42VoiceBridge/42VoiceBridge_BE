package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.ChangePasswordRequest;
import com.voicebridge.adapter.in.web.dto.MyProfileResponse;
import com.voicebridge.adapter.in.web.dto.TokenResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.ChangePasswordUseCase;
import com.voicebridge.port.in.GetMyProfileUseCase;
import com.voicebridge.port.in.ResetVoiceDataUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final GetMyProfileUseCase getMyProfileUseCase;
  private final ChangePasswordUseCase changePasswordUseCase;
  private final ResetVoiceDataUseCase resetVoiceDataUseCase;

  // 목소리와 그 결과(진단·개인화·인식·TTS)를 지운다. 계정과 내 문구는 남는다. 여러 번 불러도 결과가 같다.
  @DeleteMapping("/me/voice-data")
  public ResponseEntity<Void> resetVoiceData(@AuthenticationPrincipal UUID userId) {
    resetVoiceDataUseCase.resetVoiceData(userId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ApiResponse<MyProfileResponse> me(@AuthenticationPrincipal UUID userId) {
    var result = getMyProfileUseCase.getMyProfile(userId);
    return ApiResponse.success(MyProfileResponse.from(result));
  }

  // 새 토큰을 돌려주므로 캐시하지 않게 한다.
  @PatchMapping("/me/password")
  public ResponseEntity<ApiResponse<TokenResponse>> changePassword(
      @AuthenticationPrincipal UUID userId, @RequestBody ChangePasswordRequest request) {
    var result =
        changePasswordUseCase.changePassword(
            new ChangePasswordUseCase.ChangePasswordCommand(
                userId, request.currentPassword(), request.newPassword()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(TokenResponse.from(result)));
  }
}
