package com.voicebridge.port.out;

import java.util.List;
import java.util.UUID;

/**
 * 사용자 한 명의 음성 데이터(목소리와 그 결과)를 DB에서 한 번에 지운다. 음성 데이터 초기화와 회원 탈퇴가 쓴다.
 *
 * <p>지우는 것: 진단 녹음·세션·자모 오류 통계·추천 문장 기록, 개인화 녹음·학습 job·어댑터 기록, 실사용 인식 결과·확인 문장·TTS 요청. 내가 추가한 문구와
 * 계정은 지우지 않는다.
 *
 * <p>하나의 트랜잭션에서 지우고, 지운 행이 가리키던 저장소 key를 돌려준다. 파일은 호출한 쪽이 커밋 뒤에 지운다(DB가 실패하면 파일은 그대로 남아야 하므로). 실패는
 * CustomException으로 번역해 던진다.
 */
public interface VoiceDataErasurePort {

  List<String> eraseVoiceData(UUID userId);
}
