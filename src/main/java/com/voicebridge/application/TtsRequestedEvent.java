package com.voicebridge.application;

import java.util.UUID;

// confirmedText를 이벤트에 실어 보낸다. 요청 시점에 이미 confirmation을 조회해 메모리에 갖고 있으니,
// 핸들러가 다시 confirmation을 조회하는 왕복을 피할 수 있다.
public record TtsRequestedEvent(UUID ttsId, String confirmedText) {}
