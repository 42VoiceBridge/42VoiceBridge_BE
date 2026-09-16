package com.voicebridge.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiagnosisSessionTest {

  @Test
  void 문장_목록과_함께_시작하면_IN_PROGRESS_상태다() {
    DiagnosisSession session =
        DiagnosisSession.start(UUID.randomUUID(), List.of(UUID.randomUUID()));

    assertThat(session.getStatus()).isEqualTo(DiagnosisSessionStatus.IN_PROGRESS);
  }

  @Test
  void 문장_목록이_비어있으면_시작할_수_없다() {
    assertThatThrownBy(() -> DiagnosisSession.start(UUID.randomUUID(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void IN_PROGRESS_상태에서만_분석완료로_전이할_수_있다() {
    DiagnosisSession session =
        DiagnosisSession.start(UUID.randomUUID(), List.of(UUID.randomUUID()));

    session.markAnalyzed();

    assertThat(session.getStatus()).isEqualTo(DiagnosisSessionStatus.ANALYZED);
    assertThatThrownBy(session::markAnalyzed).isInstanceOf(IllegalStateException.class);
  }
}
