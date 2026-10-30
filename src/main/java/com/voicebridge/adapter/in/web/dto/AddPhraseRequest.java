package com.voicebridge.adapter.in.web.dto;

// 검증 규칙(빈 값, 길이, 제어문자, 카테고리 코드)은 도메인(Phrase, PhraseCategory)에만 둔다. 여기에 같은 규칙을 다시 적으면 둘이 어긋난다.
public record AddPhraseRequest(String category, String text) {}
