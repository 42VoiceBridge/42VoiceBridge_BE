package com.voicebridge.domain.personalization;

import java.util.Arrays;
import java.util.List;

/** 녹음의 공개·정리 상태 전이 규칙. 저장소는 필요한 전이에 이 규칙을 DB 갱신 조건으로 사용한다. */
public enum PersonalizationRecordingStatus {
  PREPARING,
  UPLOADED,
  CLEANUP_PENDING,
  DELETION_PENDING;

  public boolean canTransitionTo(PersonalizationRecordingStatus next) {
    return switch (this) {
      case PREPARING -> next == UPLOADED || next == CLEANUP_PENDING || next == DELETION_PENDING;
      case UPLOADED -> next == DELETION_PENDING;
      case CLEANUP_PENDING -> next == CLEANUP_PENDING || next == DELETION_PENDING;
      case DELETION_PENDING -> next == DELETION_PENDING || next == CLEANUP_PENDING;
    };
  }

  public void requireTransitionTo(PersonalizationRecordingStatus next) {
    if (!canTransitionTo(next)) {
      throw new IllegalStateException(
          "Invalid personalization recording transition: " + this + " -> " + next);
    }
  }

  public static List<String> allowedSourceNames(PersonalizationRecordingStatus next) {
    return Arrays.stream(values())
        .filter(current -> current.canTransitionTo(next))
        .map(Enum::name)
        .toList();
  }
}
