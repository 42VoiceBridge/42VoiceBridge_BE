package com.voicebridge.port.in;

import java.util.UUID;

/** 문구 추가 계약. API 명세서 10.2절 참고. 고정 카테고리 안에만 추가할 수 있고, 같은 카테고리에 이미 추가한 같은 문구나 개수 상한 초과는 409다. */
public interface AddPhraseUseCase {

  PhraseView add(AddCommand command);

  record AddCommand(UUID userId, String category, String text) {}

  record PhraseView(UUID id, String category, String text, boolean isDefault) {}
}
