package com.voicebridge.adapter.out.auth;

import com.voicebridge.port.out.TokenHasherPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * refresh token은 이미 고엔트로피 랜덤 문자열이라 BCrypt 같은 느린 솔트 해싱이 필요 없고, 오히려 BCrypt는 입력을 72바이트로 제한해 JWT처럼 긴
 * 토큰에는 쓸 수 없다. 그래서 사용자 비밀번호용 {@link com.voicebridge.port.out.PasswordEncoderPort}와 별도로 SHA-256 기반
 * 포트를 둔다.
 */
@Component
public class Sha256TokenHasherAdapter implements TokenHasherPort {

  @Override
  public String hash(String rawToken) {
    return Base64.getEncoder().encodeToString(digest(rawToken));
  }

  @Override
  public boolean matches(String rawToken, String hashedToken) {
    byte[] expected = Base64.getDecoder().decode(hashedToken);
    return MessageDigest.isEqual(digest(rawToken), expected);
  }

  private byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
    }
  }
}
