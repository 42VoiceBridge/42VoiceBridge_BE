package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.phrase.PhraseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// (owner_id, text) 유니크로 같은 사용자의 같은 문구를 DB에서도 막는다. 두 번 눌러 동시에 들어온 추가 요청도 하나만 남는다.
// owner_id가 NULL인 기본 문구끼리는 MySQL 유니크가 막지 않으므로 기본 문구 중복은 시더가 책임진다(이미 있으면 넣지 않음).
// 이 유니크 인덱스가 owner_id로 시작해서 "기본 문구 + 내 문구" 목록 조회와 개수 세기에도 쓰인다.
@Entity
@Table(
    name = "phrases",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_phrases_owner_text",
            columnNames = {"owner_id", "text"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhraseJpaEntity {

  @Id private UUID id;

  @Column(name = "owner_id")
  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PhraseCategory category;

  @Column(nullable = false, length = 100)
  private String text;

  @Column(name = "audio_storage_key")
  private String audioStorageKey;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Builder
  private PhraseJpaEntity(
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
}
