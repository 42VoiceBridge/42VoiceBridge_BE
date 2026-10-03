package com.voicebridge.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증이 필요한 요청에 유효한 토큰이 없을 때 401을 공통 응답 형식으로 돌려준다. 이게 없으면 스프링 시큐리티 기본값인 본문 없는 403이 나간다.
 *
 * <p>토큰이 왔는데 거절된 경우는 그 이유(만료 등)를 그대로 알려, 프론트가 토큰 재발급과 다시 로그인을 구분할 수 있게 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    ApiResponse<Void> body =
        request.getAttribute(JwtAuthenticationFilter.TOKEN_FAILURE)
                instanceof CustomException failure
            ? ApiResponse.error(failure.getErrorCode().name(), failure.getMessage())
            : ApiResponse.error(
                ErrorCode.AUTH_REQUIRED.name(), ErrorCode.AUTH_REQUIRED.getMessage());

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
