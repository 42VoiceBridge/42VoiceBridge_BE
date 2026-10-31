package com.voicebridge.adapter.in.web;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.TokenProviderPort;
import com.voicebridge.port.out.UserRepositoryPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String HEADER = "Authorization";
  private static final String PREFIX = "Bearer ";

  /** 토큰이 거절된 이유를 담는 요청 속성. 인증이 필요한 요청이면 JwtAuthenticationEntryPoint가 이 이유로 401 응답을 만든다. */
  static final String TOKEN_FAILURE = JwtAuthenticationFilter.class.getName() + ".TOKEN_FAILURE";

  private final TokenProviderPort tokenProviderPort;
  private final UserRepositoryPort userRepositoryPort;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = resolveToken(request);

    if (StringUtils.hasText(token)) {
      authenticate(token, request);
    }

    filterChain.doFilter(request, response);
  }

  private void authenticate(String token, HttpServletRequest request) {
    UUID userId;
    try {
      userId = tokenProviderPort.validateAndGetUserId(token);
    } catch (RuntimeException e) {
      SecurityContextHolder.clearContext();
      request.setAttribute(TOKEN_FAILURE, e);
      return;
    }
    // 탈퇴한 계정의 액세스 토큰은 만료 전(최대 1시간)이라도 받지 않는다. 받으면 없는 사용자 이름으로 데이터가 생긴다.
    // DB 장애는 토큰 문제가 아니므로 잡지 않고 그대로 올린다(401로 바꾸면 프론트가 로그아웃시킨다).
    if (!userRepositoryPort.existsById(userId)) {
      SecurityContextHolder.clearContext();
      request.setAttribute(
          TOKEN_FAILURE, new CustomException(ErrorCode.AUTH_TOKEN_EXPIRED, "유효하지 않은 토큰입니다."));
      return;
    }
    var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HEADER);
    if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
      return header.substring(PREFIX.length());
    }
    return null;
  }
}
