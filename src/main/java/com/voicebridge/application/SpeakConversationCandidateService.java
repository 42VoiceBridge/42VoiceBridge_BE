package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.conversation.ConversationPolicy;
import com.voicebridge.domain.conversation.ConversationTurn;
import com.voicebridge.port.in.SpeakConversationCandidateUseCase;
import com.voicebridge.port.out.CallRateLimitPort;
import com.voicebridge.port.out.ConversationTurnStorePort;
import com.voicebridge.port.out.TtsEnginePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 대화 모드 후보 말하기. 말할 텍스트는 요청에서 받지 않고 턴 저장소에서 꺼낸다 — 서버가 이 턴에 발급한 후보만 말할 수 있다. 합성한 음성은 저장하지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakConversationCandidateService implements SpeakConversationCandidateUseCase {

  static final String FEATURE = "conversation-speak";

  private final ConversationTurnStorePort turnStorePort;
  private final CallRateLimitPort callRateLimitPort;
  private final TtsEnginePort ttsEnginePort;

  @Override
  public byte[] speak(SpeakCommand command) {
    if (command.userId() == null || command.turnId() == null || command.candidateId() == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    // 만료된 턴은 없는 턴과 같다
    ConversationTurn turn =
        turnStorePort
            .findById(command.turnId())
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!turn.isOwnedBy(command.userId())) {
      throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
    }
    ConversationTurn.Candidate candidate =
        turn.findCandidate(command.candidateId())
            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

    if (!callRateLimitPort.tryAcquire(
        command.userId(),
        FEATURE,
        ConversationPolicy.MAX_SPEAKS_PER_MINUTE,
        ConversationPolicy.RATE_WINDOW)) {
      throw new CustomException(ErrorCode.TOO_MANY_REQUESTS);
    }

    byte[] audio;
    try {
      audio = ttsEnginePort.synthesize(candidate.text());
    } catch (RuntimeException e) {
      // 예외 메시지에 텍스트가 섞일 수 있어 예외 종류만 남긴다
      log.warn("[대화 모드] 음성 합성 실패 turnId={} cause={}", turn.getId(), e.getClass().getSimpleName());
      throw new CustomException(ErrorCode.TTS_UNAVAILABLE);
    }
    if (audio == null || audio.length == 0) {
      throw new CustomException(ErrorCode.TTS_UNAVAILABLE);
    }
    return audio;
  }
}
