package com.voicebridge.port.out;

import java.util.List;
import java.util.UUID;

/**
 * 회원 탈퇴: 사용자 한 명의 모든 데이터를 DB에서 한 번에 지운다. 음성 데이터(VoiceDataErasurePort와 같은 범위)에 더해 내가 추가한 문구와 계정까지
 * 지운다.
 *
 * <p>하나의 트랜잭션에서 지우고, 지운 행이 가리키던 저장소 key를 돌려준다. 파일은 호출한 쪽이 커밋 뒤에 지운다. 실패는 CustomException으로 번역해 던진다.
 */
public interface AccountErasurePort {

  List<String> eraseAccount(UUID userId);
}
