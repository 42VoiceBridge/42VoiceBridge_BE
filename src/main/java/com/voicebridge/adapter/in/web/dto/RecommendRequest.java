package com.voicebridge.adapter.in.web.dto;

/** count의 기본값과 허용 범위는 도메인(RecommendationCount)이 정한다. 여기에 같은 규칙을 두지 않는다. */
public record RecommendRequest(Integer count) {}
