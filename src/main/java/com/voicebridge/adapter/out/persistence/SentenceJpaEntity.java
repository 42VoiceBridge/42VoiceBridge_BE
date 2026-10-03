package com.voicebridge.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sentences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentenceJpaEntity {

  @Id private UUID id;

  private String text;

  @Builder
  private SentenceJpaEntity(UUID id, String text) {
    this.id = id;
    this.text = text;
  }
}
