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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// (owner_id, category, text_sha256) 유니크로 같은 사용자의 같은 카테고리 같은 문구를 DB에서도 막는다. 두 번 눌러 동시에 들어온 추가 요청도
// 하나만 남는다.
// text가 아니라 해시에 거는 이유: utf8mb4_0900_ai_ci는 대소문자·악센트를 무시하고 비교해서 "OK"와 "ok"를 같은 값으로 본다. 해시는 글자가 정확히
// 같을 때만 같다.
// owner_id가 NULL인 기본 문구끼리는 MySQL 유니크가 막지 않으므로 기본 문구 중복은 시더가 책임진다(이미 있으면 넣지 않음).
// 이 유니크 인덱스가 (owner_id, category)로 시작해서 "기본 + 내 문구(+ 카테고리)" 목록 조회와 개수 세기에도 쓰인다. 같은 앞부분의 인덱스를 따로 두지
// 않는다.
@Entity
@Table(
    name = "phrases",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_phrases_owner_category_text",
            columnNames = {"owner_id", "category", "text_sha256"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhraseJpaEntity {

  @Id private UUID id;

  @Column(name = "owner_id")
  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PhraseCategory category;

  // 도메인 상한(Phrase.MAX_TEXT_LENGTH, 100자)보다 넉넉하게 둔다. 정책값을 올려도 스키마를 바꾸지 않아도 되게.
  @Column(nullable = false, length = 200)
  private String text;

  // 길이가 항상 64자라 CHAR(64)로 둔다. JDBC 타입을 CHAR로 지정해야 운영의 validate가 V2의 CHAR 컬럼과 맞춰 본다.
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "text_sha256", nullable = false, length = 64)
  private String textSha256;

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
      String textSha256,
      String audioStorageKey,
      LocalDateTime createdAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.category = category;
    this.text = text;
    this.textSha256 = textSha256;
    this.audioStorageKey = audioStorageKey;
    this.createdAt = createdAt;
  }
}
