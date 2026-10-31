package com.voicebridge.adapter.out.stt;

import com.voicebridge.port.out.SpeechToTextPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 로컬 개발 전용. CLOVA STT 키 없이도 대화 모드(녹음 → 들은 말 → 답변 후보 → 말하기)를 끝까지 돌려보기 위한 대역이다.
// 답변 후보가 의미 있게 나오도록 병원에서 들을 법한 질문을 돌려준다.
@Slf4j
@Component
@Profile("local")
public class StubSpeechToTextAdapter implements SpeechToTextPort {

  static final String STUB_TEXT = "어디가 불편해서 오셨어요?";

  @Override
  public String transcribe(byte[] wavBytes) {
    // 실제 어댑터와 같은 입력 검증을 해야 로컬에서만 통과하는 경로가 생기지 않는다.
    if (wavBytes == null || wavBytes.length == 0) {
      throw new IllegalArgumentException("인식할 음성이 없습니다.");
    }
    log.info("[스텁 STT] 실제 CLOVA를 호출하지 않는다. bytes={}", wavBytes.length);
    return STUB_TEXT;
  }
}
