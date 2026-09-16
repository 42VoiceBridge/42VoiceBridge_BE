package com.voicebridge.adapter.out.auth;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.port.out.TokenProviderPort;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider implements TokenProviderPort {

  private final SecretKey key;
  private final long accessTokenExpireSeconds;
  private final long refreshTokenExpireSeconds;

  public JwtTokenProvider(
      @Value("${voicebridge.jwt.secret}") String secret,
      @Value("${voicebridge.jwt.access-token-expire-seconds}") long accessTokenExpireSeconds,
      @Value("${voicebridge.jwt.refresh-token-expire-seconds}") long refreshTokenExpireSeconds) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpireSeconds = accessTokenExpireSeconds;
    this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
  }

  @Override
  public String createAccessToken(UUID userId) {
    return createToken(userId, accessTokenExpireSeconds);
  }

  @Override
  public String createRefreshToken(UUID userId) {
    return createToken(userId, refreshTokenExpireSeconds);
  }

  @Override
  public long getAccessTokenExpireSeconds() {
    return accessTokenExpireSeconds;
  }

  @Override
  public UUID validateAndGetUserId(String token) {
    try {
      String subject =
          Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
      return UUID.fromString(subject);
    } catch (ExpiredJwtException e) {
      throw new CustomException(ErrorCode.AUTH_TOKEN_EXPIRED);
    } catch (JwtException | IllegalArgumentException e) {
      throw new CustomException(ErrorCode.AUTH_TOKEN_EXPIRED, "유효하지 않은 토큰입니다.");
    }
  }

  private String createToken(UUID userId, long expireSeconds) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expireSeconds)))
        .signWith(key)
        .compact();
  }
}
