package com.voicebridge.adapter.in.web;

import com.voicebridge.adapter.in.web.dto.KakaoLoginRequest;
import com.voicebridge.adapter.in.web.dto.LoginRequest;
import com.voicebridge.adapter.in.web.dto.RefreshRequest;
import com.voicebridge.adapter.in.web.dto.SignUpRequest;
import com.voicebridge.adapter.in.web.dto.SignUpResponse;
import com.voicebridge.adapter.in.web.dto.TokenResponse;
import com.voicebridge.common.response.ApiResponse;
import com.voicebridge.port.in.KakaoLoginUseCase;
import com.voicebridge.port.in.LoginUseCase;
import com.voicebridge.port.in.RefreshTokenUseCase;
import com.voicebridge.port.in.SignUpUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final SignUpUseCase signUpUseCase;
  private final LoginUseCase loginUseCase;
  private final KakaoLoginUseCase kakaoLoginUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;

  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<SignUpResponse>> signUp(
      @Valid @RequestBody SignUpRequest request) {
    var result = signUpUseCase.signUp(request.toCommand());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(SignUpResponse.from(result)));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<TokenResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    var result = loginUseCase.login(request.toCommand());
    return ResponseEntity.ok(ApiResponse.success(TokenResponse.from(result)));
  }

  @PostMapping("/kakao")
  public ResponseEntity<ApiResponse<TokenResponse>> loginWithKakao(
      @Valid @RequestBody KakaoLoginRequest request) {
    var result = kakaoLoginUseCase.loginWithKakao(request.kakaoAccessToken());
    return ResponseEntity.ok(ApiResponse.success(TokenResponse.from(result)));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<TokenResponse>> refresh(
      @Valid @RequestBody RefreshRequest request) {
    var result = refreshTokenUseCase.refresh(request.refreshToken());
    return ResponseEntity.ok(ApiResponse.success(TokenResponse.from(result)));
  }
}
