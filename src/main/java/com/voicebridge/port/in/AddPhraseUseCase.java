package com.voicebridge.port.in;

import java.util.UUID;

/** 문구 추가 계약. API 명세서 10.2절 참고. 고정 카테고리 안에만 추가할 수 있고, 이미 보이는 문구와 같은 텍스트나 개수 상한 초과는 409다. */
public interface AddPhraseUseCase {

  AddedPhrase add(AddCommand command);

  record AddCommand(UUID userId, String category, String text) {}

  record AddedPhrase(UUID phraseId, String category, String text) {}
}
