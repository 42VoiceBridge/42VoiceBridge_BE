package com.voicebridge.domain.personalization;

public class InsufficientRecordingException extends RuntimeException {

  public InsufficientRecordingException() {
    super("개인화 학습에 필요한 녹음 수가 부족합니다.");
  }
}
