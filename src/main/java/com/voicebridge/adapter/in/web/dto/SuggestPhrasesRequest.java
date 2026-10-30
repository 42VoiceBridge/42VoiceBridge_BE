package com.voicebridge.adapter.in.web.dto;

// 검증 규칙(빈 값, 길이, 제어문자, 카테고리 코드)은 도메인에만 둔다.
public record SuggestPhrasesRequest(String text, String category) {}
