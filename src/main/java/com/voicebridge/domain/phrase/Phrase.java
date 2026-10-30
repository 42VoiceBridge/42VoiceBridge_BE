package com.voicebridge.domain.phrase;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 자주 쓰는 문구. 누르면 TTS로 읽어 준다. 소유자가 없으면(ownerId == null) 모든 사용자에게 보이는 기본 문구, 있으면 그 사용자가 직접 추가한 문구다.
 *
 * <p>합성한 음성은 문구마다 한 번만 만들어 저장 key를 붙여 둔다(audioStorageKey). 문구 텍스트는 바뀌지 않으므로(수정 기능 없음, 결정 D3) 한 번 만든
 * 음성을 계속 쓸 수 있다.
 */
public class Phrase {

  /** 사용자 한 명이 직접 추가할 수 있는 문구 수. 기본 문구는 세지 않는다. */
  public static final int MAX_CUSTOM_PHRASES_PER_USER = 50;

  /** 문구 길이 상한(글자 수). 화면 버튼에 들어가고 한 번에 읽어 줄 만한 길이다. */
  public static final int MAX_TEXT_LENGTH = 100;

  private final UUID id;
  private final UUID ownerId;
  private final PhraseCategory category;
  private final String text;
  private String audioStorageKey;
  private final LocalDateTime createdAt;

  private Phrase(
      UUID id,
      UUID ownerId,
      PhraseCategory category,
      String text,
      String audioStorageKey,
      LocalDateTime createdAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.category = category;
    this.text = text;
    this.audioStorageKey = audioStorageKey;
    this.createdAt = createdAt;
  }

  /** 사용자가 직접 추가하는 문구. 앞뒤 공백은 지우고 저장한다. */
  public static Phrase createCustom(UUID ownerId, PhraseCategory category, String text) {
    if (ownerId == null) {
      throw new IllegalArgumentException("직접 추가하는 문구에는 사용자가 있어야 합니다.");
    }
    return create(ownerId, category, text);
  }

  /** 모든 사용자에게 보이는 기본 문구. 시드 데이터에서만 만든다. */
  public static Phrase createDefault(PhraseCategory category, String text) {
    return create(null, category, text);
  }

  private static Phrase create(UUID ownerId, PhraseCategory category, String text) {
    if (category == null) {
      throw new IllegalArgumentException("카테고리를 선택해 주세요.");
    }
    return new Phrase(
        UUID.randomUUID(), ownerId, category, normalize(text), null, LocalDateTime.now());
  }

  /** 영속성 어댑터가 DB에서 읽어온 값을 그대로 도메인 객체로 복원할 때만 사용한다. */
  public static Phrase reconstitute(
      UUID id,
      UUID ownerId,
      PhraseCategory category,
      String text,
      String audioStorageKey,
      LocalDateTime createdAt) {
    return new Phrase(id, ownerId, category, text, audioStorageKey, createdAt);
  }

  // 줄바꿈·탭 같은 제어문자는 버튼 표시와 TTS 입력을 깨뜨리므로 받지 않는다. 길이는 UTF-16 단위가 아니라 글자(코드 포인트) 수로 센다.
  private static String normalize(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("문구를 입력해 주세요.");
    }
    String stripped = text.strip();
    if (stripped.codePoints().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("문구에 줄바꿈이나 제어문자를 넣을 수 없습니다.");
    }
    if (stripped.codePointCount(0, stripped.length()) > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("문구는 " + MAX_TEXT_LENGTH + "자 이하로 입력해 주세요.");
    }
    return stripped;
  }

  /** 지금 개수에서 하나를 더 추가할 수 없으면 true. */
  public static boolean hasReachedCustomLimit(long customPhraseCount) {
    return customPhraseCount >= MAX_CUSTOM_PHRASES_PER_USER;
  }

  public boolean isDefault() {
    return ownerId == null;
  }

  public boolean isOwnedBy(UUID userId) {
    return ownerId != null && ownerId.equals(userId);
  }

  /** 기본 문구는 누구나, 직접 추가한 문구는 본인만 쓸 수 있다. */
  public boolean isUsableBy(UUID userId) {
    return isDefault() || isOwnedBy(userId);
  }

  public boolean hasAudio() {
    return audioStorageKey != null;
  }

  /**
   * 합성한 음성의 저장 key를 붙인다. 이미 붙어 있으면 무시하고 false를 돌려준다 — 동시에 두 요청이 합성했을 때 먼저 붙은 음성을 유지하고, 나중 것은 호출한 쪽이
   * 정리한다.
   */
  public boolean attachAudio(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalArgumentException("음성 저장 경로가 필요합니다.");
    }
    if (hasAudio()) {
      return false;
    }
    this.audioStorageKey = storageKey;
    return true;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public PhraseCategory getCategory() {
    return category;
  }

  public String getText() {
    return text;
  }

  public String getAudioStorageKey() {
    return audioStorageKey;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
