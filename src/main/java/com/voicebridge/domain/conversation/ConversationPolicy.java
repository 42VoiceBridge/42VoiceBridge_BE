package com.voicebridge.domain.conversation;

import java.time.Duration;

/**
 * 대화 모드 정책값. 값은 가정치다(부록 D9) — 실제 사용량과 요금을 보고 조정한다.
 *
 * <p>턴은 화면에서 답을 고르는 동안만 필요하므로 짧게 둔다. 만료되면 들은 말과 후보가 Redis에서 사라진다.
 */
public final class ConversationPolicy {

  /** 턴(들은 말·후보)을 보관하는 시간. */
  public static final Duration TURN_TTL = Duration.ofMinutes(10);

  /** 사용자당 분당 턴 생성 횟수(받아쓰기 + LLM 호출). */
  public static final int MAX_TURNS_PER_MINUTE = 10;

  /** 사용자당 분당 후보 말하기 횟수(TTS 호출). 한 후보를 여러 번 눌러도 센다. */
  public static final int MAX_SPEAKS_PER_MINUTE = 20;

  public static final Duration RATE_WINDOW = Duration.ofMinutes(1);

  private ConversationPolicy() {}
}
