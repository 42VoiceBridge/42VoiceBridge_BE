package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.conversation.ConversationPolicy;
import com.voicebridge.domain.conversation.ConversationTurn;
import com.voicebridge.domain.suggestion.SuggestionPolicy;
import com.voicebridge.port.in.StartConversationTurnUseCase;
import com.voicebridge.port.out.AudioNormalizationPort;
import com.voicebridge.port.out.CallRateLimitPort;
import com.voicebridge.port.out.ConversationTurnStorePort;
import com.voicebridge.port.out.InvalidAudioException;
import com.voicebridge.port.out.LlmSuggestionPort;
import com.voicebridge.port.out.SpeechToTextPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 대화 모드 턴 시작. DB를 쓰지 않으므로 @Transactional이 없다(외부 호출 STT·LLM이 트랜잭션 안에 들어갈 일도 없다).
 *
 * <p>녹음은 어디에도 저장하지 않는다. 들은 말과 후보는 턴 저장소(TTL)에만 두고 로그에는 개수만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartConversationTurnService implements StartConversationTurnUseCase {

  static final String FEATURE = "conversation";

  private final AudioNormalizationPort audioNormalizationPort;
  private final CallRateLimitPort callRateLimitPort;
  private final SpeechToTextPort speechToTextPort;
  private final LlmSuggestionPort llmSuggestionPort;
  private final ConversationTurnStorePort turnStorePort;

  @Override
  public TurnResult start(StartCommand command) {
    if (command.userId() == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    if (command.audioBytes() == null || command.audioBytes().length == 0) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED, "녹음 파일이 비어 있습니다.");
    }
    // 녹음 검증을 상한보다 먼저 한다. 잘못된 녹음이 호출 횟수를 깎지 않게. 변환기 용량·시간 초과는 전역 핸들러가 503으로 바꾼다.
    byte[] wav = normalize(command.audioBytes());

    if (!callRateLimitPort.tryAcquire(
        command.userId(),
        FEATURE,
        ConversationPolicy.MAX_TURNS_PER_MINUTE,
        ConversationPolicy.RATE_WINDOW)) {
      throw new CustomException(ErrorCode.TOO_MANY_REQUESTS);
    }

    String heardText = speechToTextPort.transcribe(wav);
    ConversationTurn turn =
        ConversationTurn.create(command.userId(), heardText, replyCandidates(heardText));
    turnStorePort.save(turn, ConversationPolicy.TURN_TTL);

    log.info("[대화 모드] 턴 생성 turnId={} 후보 {}개", turn.getId(), turn.getCandidates().size());
    return new TurnResult(
        turn.getId(),
        turn.getHeardText(),
        turn.getCandidates().stream().map(c -> new Candidate(c.id(), c.text())).toList());
  }

  // 후보를 못 만들어도 들은 말은 보여 준다. 사용자는 문구함이나 말하기로 대답할 수 있다.
  private List<String> replyCandidates(String heardText) {
    try {
      return SuggestionPolicy.filter(llmSuggestionPort.replyTo(heardText));
    } catch (CustomException e) {
      log.warn("[대화 모드] 답변 후보를 만들지 못함 code={}", e.getErrorCode());
      return List.of();
    }
  }

  private byte[] normalize(byte[] audioBytes) {
    try {
      return audioNormalizationPort.normalize(audioBytes).wavBytes();
    } catch (InvalidAudioException e) {
      throw new CustomException(
          switch (e.reason()) {
            case TOO_SHORT -> ErrorCode.AUDIO_TOO_SHORT;
            case TOO_LONG -> ErrorCode.AUDIO_TOO_LONG;
            case INVALID -> ErrorCode.AUDIO_INVALID;
          });
    }
  }
}
