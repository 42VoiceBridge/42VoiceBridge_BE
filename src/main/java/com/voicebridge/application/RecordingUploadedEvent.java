package com.voicebridge.application;

import java.util.UUID;

// 오디오 바이트를 이벤트에 실어 보낸다. 업로드 요청에서 이미 메모리에 있는 값이라 S3에 썼다가 다시 읽는 왕복을 피할 수 있다.
// 다만 나중에 재시도 기능을 넣는다면, 재시도 시점에는 이 바이트가 없으므로 그때는 스토리지에서 다시 읽어야 한다.
public record RecordingUploadedEvent(UUID recordingId, byte[] audioBytes) {}
