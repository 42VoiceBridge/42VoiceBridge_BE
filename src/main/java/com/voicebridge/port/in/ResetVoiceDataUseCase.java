package com.voicebridge.port.in;

import java.util.UUID;

/**
 * 음성 데이터 초기화 계약. API 명세서 1.7절 참고. 사용자의 목소리와 그 결과(진단·개인화·실사용 인식·TTS)를 지우고, 계정과 내가 추가한 문구는 남긴다. 여러 번
 * 호출해도 결과가 같다.
 */
public interface ResetVoiceDataUseCase {

  void resetVoiceData(UUID userId);
}
