package com.voicebridge.application;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.port.in.AddPhraseUseCase;
import com.voicebridge.port.out.PhraseRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AddPhraseService implements AddPhraseUseCase {

  private final PhraseRepositoryPort phraseRepositoryPort;

  @Override
  public AddedPhrase add(AddCommand command) {
    if (command.userId() == null) {
      throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
    // 입력 검증(400)이 상한·중복(409)보다 먼저다. 잘못된 입력이면 개수와 상관없이 고쳐서 다시 보내야 한다.
    Phrase phrase =
        Phrase.createCustom(
            command.userId(), PhraseCategory.from(command.category()), command.text());

    if (Phrase.hasReachedCustomLimit(phraseRepositoryPort.countCustomByOwner(command.userId()))) {
      throw new CustomException(
          ErrorCode.PHRASE_LIMIT_EXCEEDED,
          "직접 추가한 문구는 " + Phrase.MAX_CUSTOM_PHRASES_PER_USER + "개까지 둘 수 있습니다. 쓰지 않는 문구를 지워 주세요.");
    }
    // 기본 문구와 같은 텍스트도 막는다. 같은 버튼이 두 번 보이면 헷갈린다. 동시에 들어온 같은 요청은 저장할 때 DB 유니크가 막는다.
    if (phraseRepositoryPort.existsVisibleText(command.userId(), phrase.getText())) {
      throw new CustomException(ErrorCode.PHRASE_DUPLICATED);
    }

    Phrase saved = phraseRepositoryPort.save(phrase);
    return new AddedPhrase(saved.getId(), saved.getCategory().name(), saved.getText());
  }
}
