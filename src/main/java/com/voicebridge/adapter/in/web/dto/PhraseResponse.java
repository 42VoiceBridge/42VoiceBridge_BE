package com.voicebridge.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.voicebridge.port.in.AddPhraseUseCase;
import com.voicebridge.port.in.ListPhrasesUseCase;
import java.util.UUID;

// 목록과 추가 응답이 같은 모양이다(API 명세서 10.1·10.2). is로 시작하는 boolean은 직렬화 도구에 따라 "default"로 바뀔 수 있어 이름을 고정한다.
public record PhraseResponse(
    UUID id, String category, String text, @JsonProperty("isDefault") boolean isDefault) {

  public static PhraseResponse from(ListPhrasesUseCase.PhraseView view) {
    return new PhraseResponse(view.id(), view.category(), view.text(), view.isDefault());
  }

  public static PhraseResponse from(AddPhraseUseCase.PhraseView view) {
    return new PhraseResponse(view.id(), view.category(), view.text(), view.isDefault());
  }
}
