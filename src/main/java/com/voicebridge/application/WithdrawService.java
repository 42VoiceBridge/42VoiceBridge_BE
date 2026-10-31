package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.user.User;
import com.voicebridge.port.in.WithdrawUseCase;
import com.voicebridge.port.out.AccountErasurePort;
import com.voicebridge.port.out.PasswordEncoderPort;
import com.voicebridge.port.out.RefreshTokenStorePort;
import com.voicebridge.port.out.StoragePort;
import com.voicebridge.port.out.UserRepositoryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴. @Transactional을 두지 않는다 — DB 삭제는 AccountErasurePort가 자기 트랜잭션에서 끝내고(커밋), 리프레시 토큰(Redis)과 저장소
 * 파일은 그 뒤에 지운다. 파일을 먼저 지우면 DB가 실패했을 때 계정은 남고 파일만 사라진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService implements WithdrawUseCase {

  private final UserRepositoryPort userRepositoryPort;
  private final PasswordEncoderPort passwordEncoderPort;
  private final AccountErasurePort accountErasurePort;
  private final RefreshTokenStorePort refreshTokenStorePort;
  private final StoragePort storagePort;

  @Override
  public void withdraw(WithdrawCommand command) {
    User user =
        userRepositoryPort
            .findById(command.userId())
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    if (user.isLocalUser()) {
      verifyPassword(user, command.password());
    }

    List<String> storageKeys = accountErasurePort.eraseAccount(user.getId());
    revokeRefreshToken(user);
    int failed = deleteFiles(storageKeys);
    log.info("[회원 탈퇴] userId={} 파일 {}개 중 삭제 실패 {}개", user.getId(), storageKeys.size(), failed);
  }

  // 탈퇴는 되돌릴 수 없어 이메일 회원은 비밀번호를 다시 받는다. 카카오 회원은 비밀번호가 없다.
  private void verifyPassword(User user, String password) {
    if (password == null || password.isBlank()) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "비밀번호를 입력해 주세요.");
    }
    if (!passwordEncoderPort.matches(password, user.getPassword())) {
      throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
    }
  }

  // 계정은 이미 지워졌다. 리프레시 토큰을 못 지워도 재발급은 사용자가 없어 실패하므로 탈퇴를 실패로 돌리지 않는다.
  private void revokeRefreshToken(User user) {
    try {
      refreshTokenStorePort.revoke(user.getId());
    } catch (RuntimeException e) {
      log.warn(
          "[회원 탈퇴] 리프레시 토큰 삭제 실패 userId={} cause={}", user.getId(), e.getClass().getSimpleName());
    }
  }

  // DB 행은 이미 지워졌으므로 되돌리지 않는다. 지우지 못한 파일은 key만 남겨 나중에 정리한다(key는 무작위 UUID라 개인정보가 없다).
  private int deleteFiles(List<String> storageKeys) {
    int failed = 0;
    for (String key : storageKeys) {
      try {
        storagePort.delete(key);
      } catch (RuntimeException e) {
        failed++;
        log.warn("[회원 탈퇴] 파일 삭제 실패 key={} cause={}", key, e.getClass().getSimpleName());
      }
    }
    return failed;
  }
}
