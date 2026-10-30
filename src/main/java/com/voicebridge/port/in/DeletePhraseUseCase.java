package com.voicebridge.port.in;

import java.util.UUID;

/** 문구 삭제 계약. API 명세서 10.3절 참고. 본인이 추가한 문구만 지울 수 있고 기본 문구는 지울 수 없다. */
public interface DeletePhraseUseCase {

  void delete(UUID userId, UUID phraseId);
}
