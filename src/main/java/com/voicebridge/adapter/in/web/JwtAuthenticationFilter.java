package com.voicebridge.adapter.in.web;

import com.voicebridge.port.out.TokenProviderPort;
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

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = resolveToken(request);

    if (StringUtils.hasText(token)) {
      try {
        UUID userId = tokenProviderPort.validateAndGetUserId(token);
        var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (RuntimeException e) {
        SecurityContextHolder.clearContext();
        request.setAttribute(TOKEN_FAILURE, e);
      }
    }

    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HEADER);
    if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
      return header.substring(PREFIX.length());
    }
    return null;
  }
}
