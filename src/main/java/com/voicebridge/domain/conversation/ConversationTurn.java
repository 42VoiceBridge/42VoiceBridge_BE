package com.voicebridge.domain.conversation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 대화 모드의 한 턴: 상대가 한 말(받아쓴 텍스트)과 서버가 만든 답변 후보. DB에 저장하지 않고 짧은 TTL로만 보관한다.
 *
 * <p>후보마다 서버가 ID를 붙인다. 말하기는 이 턴에 있는 후보 ID로만 할 수 있다 — 클라이언트가 보낸 임의의 텍스트를 TTS하지 않기 위해서다.
 */
public class ConversationTurn {

  private final UUID id;
  private final UUID ownerId;
  private final String heardText;
  private final List<Candidate> candidates;
  private final LocalDateTime createdAt;

  private ConversationTurn(
      UUID id,
      UUID ownerId,
      String heardText,
      List<Candidate> candidates,
      LocalDateTime createdAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.heardText = heardText;
    this.candidates = List.copyOf(candidates);
    this.createdAt = createdAt;
  }

  /** 거르기가 끝난 후보 텍스트로 턴을 만든다. 후보가 없어도 된다(LLM 실패 시 들은 말만 보여 준다). */
  public static ConversationTurn create(
      UUID ownerId, String heardText, List<String> candidateTexts) {
    if (ownerId == null) {
      throw new IllegalArgumentException("턴에는 사용자가 있어야 합니다.");
    }
    if (heardText == null || heardText.isBlank()) {
      throw new IllegalArgumentException("들은 말이 비어 있습니다.");
    }
    List<Candidate> candidates =
        candidateTexts.stream().map(text -> new Candidate(UUID.randomUUID(), text)).toList();
    return new ConversationTurn(
        UUID.randomUUID(), ownerId, heardText, candidates, LocalDateTime.now());
  }

  /** 저장소에서 읽어온 값을 그대로 복원할 때만 사용한다. */
  public static ConversationTurn reconstitute(
      UUID id,
      UUID ownerId,
      String heardText,
      List<Candidate> candidates,
      LocalDateTime createdAt) {
    return new ConversationTurn(id, ownerId, heardText, candidates, createdAt);
  }

  public boolean isOwnedBy(UUID userId) {
    return ownerId.equals(userId);
  }

  public Optional<Candidate> findCandidate(UUID candidateId) {
    return candidates.stream().filter(c -> c.id().equals(candidateId)).findFirst();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getHeardText() {
    return heardText;
  }

  public List<Candidate> getCandidates() {
    return candidates;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public record Candidate(UUID id, String text) {
    public Candidate {
      Objects.requireNonNull(id);
      Objects.requireNonNull(text);
    }
  }
}
