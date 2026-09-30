package com.voicebridge.application;

import java.util.UUID;

/** 진단 녹음 하나가 인식 완료(DONE)로 커밋됐음을 알린다. 세션 전체가 끝났는지는 받는 쪽이 커밋 이후에 판단한다. */
public record RecordingRecognizedEvent(UUID sessionId) {}
