package com.voicebridge.domain.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationTurnTest {

  private final UUID me = UUID.randomUUID();

  @Test
  void 후보마다_서로_다른_ID를_붙인다() {
    ConversationTurn turn = ConversationTurn.create(me, "식사하셨어요?", List.of("네.", "아니요."));

    assertThat(turn.getCandidates())
        .extracting(ConversationTurn.Candidate::text)
        .containsExactly("네.", "아니요.");
    assertThat(turn.getCandidates())
        .extracting(ConversationTurn.Candidate::id)
        .doesNotHaveDuplicates()
        .doesNotContainNull();
  }

  @Test
  void 이_턴의_후보만_찾는다() {
    ConversationTurn turn = ConversationTurn.create(me, "식사하셨어요?", List.of("네."));
    UUID issued = turn.getCandidates().get(0).id();

    assertThat(turn.findCandidate(issued)).map(ConversationTurn.Candidate::text).contains("네.");
    assertThat(turn.findCandidate(UUID.randomUUID())).isEmpty();
  }

  @Test
  void 후보가_없어도_턴을_만든다() {
    assertThat(ConversationTurn.create(me, "식사하셨어요?", List.of()).getCandidates()).isEmpty();
  }

  @Test
  void 소유자만_자기_턴이다() {
    ConversationTurn turn = ConversationTurn.create(me, "식사하셨어요?", List.of());

    assertThat(turn.isOwnedBy(me)).isTrue();
    assertThat(turn.isOwnedBy(UUID.randomUUID())).isFalse();
    assertThat(turn.isOwnedBy(null)).isFalse();
  }

  @Test
  void 사용자나_들은_말이_없으면_만들지_않는다() {
    assertThatThrownBy(() -> ConversationTurn.create(null, "식사하셨어요?", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ConversationTurn.create(me, " ", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
