package com.voicebridge.domain.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmationTest {

  @Test
  void 생성하면_valid가_true다() {
    Confirmation confirmation =
        Confirmation.create(UUID.randomUUID(), UUID.randomUUID(), "물 좀 주세요");

    assertThat(confirmation.isValid()).isTrue();
    assertThat(confirmation.getConfirmedText()).isEqualTo("물 좀 주세요");
    assertThat(confirmation.getCreatedAt()).isNotNull();
  }

  @Test
  void 확인텍스트가_null이면_생성할_수_없다() {
    assertThatThrownBy(() -> Confirmation.create(UUID.randomUUID(), UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 확인텍스트가_빈문자열이면_생성할_수_없다() {
    assertThatThrownBy(() -> Confirmation.create(UUID.randomUUID(), UUID.randomUUID(), "   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidate_호출하면_valid가_false가_된다() {
    Confirmation confirmation =
        Confirmation.create(UUID.randomUUID(), UUID.randomUUID(), "물 좀 주세요");

    confirmation.invalidate();

    assertThat(confirmation.isValid()).isFalse();
  }

  @Test
  void 이미_무효화된_것을_다시_invalidate해도_예외없이_무효_상태를_유지한다() {
    Confirmation confirmation =
        Confirmation.create(UUID.randomUUID(), UUID.randomUUID(), "물 좀 주세요");
    confirmation.invalidate();

    confirmation.invalidate();

    assertThat(confirmation.isValid()).isFalse();
  }

  @Test
  void 본인_소유_여부를_확인한다() {
    UUID userId = UUID.randomUUID();
    Confirmation confirmation = Confirmation.create(UUID.randomUUID(), userId, "물 좀 주세요");

    assertThat(confirmation.isOwnedBy(userId)).isTrue();
    assertThat(confirmation.isOwnedBy(UUID.randomUUID())).isFalse();
  }
}
