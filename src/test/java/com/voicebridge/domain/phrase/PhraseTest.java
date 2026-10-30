package com.voicebridge.domain.phrase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PhraseTest {

  private final UUID owner = UUID.randomUUID();

  @Test
  void 앞뒤_공백을_지우고_저장한다() {
    Phrase phrase = Phrase.createForUser(owner, PhraseCategory.DAILY, "  물 좀 주세요.  ");

    assertThat(phrase.getText()).isEqualTo("물 좀 주세요.");
    assertThat(phrase.getOwnerId()).isEqualTo(owner);
    assertThat(phrase.hasAudio()).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t"})
  void 비어_있거나_공백뿐이면_거절한다(String text) {
    assertThatThrownBy(() -> Phrase.createForUser(owner, PhraseCategory.DAILY, text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("문구를 입력해 주세요.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"물\n주세요", "물\t주세요", "물\u0000주세요", "물\u007f주세요"})
  void 줄바꿈과_제어문자는_거절한다(String text) {
    assertThatThrownBy(() -> Phrase.createForUser(owner, PhraseCategory.DAILY, text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("제어문자");
  }

  @Test
  void 길이는_글자_수로_세고_100자까지_받는다() {
    String hundred = "가".repeat(Phrase.MAX_TEXT_LENGTH);
    assertThat(Phrase.createForUser(owner, PhraseCategory.DAILY, hundred).getText())
        .hasSize(Phrase.MAX_TEXT_LENGTH);

    assertThatThrownBy(() -> Phrase.createForUser(owner, PhraseCategory.DAILY, hundred + "가"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("문구는 100자 이하로 입력해 주세요.");
  }

  @Test
  void 이모지처럼_두_칸을_쓰는_글자도_한_글자로_센다() {
    // 😀는 UTF-16으로 2칸이다. String.length()로 세면 50개만 받아야 하지만 글자로는 100자다.
    String emojis = "😀".repeat(Phrase.MAX_TEXT_LENGTH);

    assertThat(Phrase.createForUser(owner, PhraseCategory.DAILY, emojis).getText())
        .isEqualTo(emojis);
  }

  @Test
  void 카테고리가_없으면_거절한다() {
    assertThatThrownBy(() -> Phrase.createForUser(owner, null, "물 좀 주세요."))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 직접_추가하는_문구는_소유자가_있어야_한다() {
    assertThatThrownBy(() -> Phrase.createForUser(null, PhraseCategory.DAILY, "물 좀 주세요."))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 기본_문구는_누구나_쓰고_직접_추가한_문구는_본인만_쓴다() {
    Phrase defaults = Phrase.createDefault(PhraseCategory.HOSPITAL, "안녕하세요.");
    Phrase mine = Phrase.createForUser(owner, PhraseCategory.DAILY, "물 좀 주세요.");
    UUID other = UUID.randomUUID();

    assertThat(defaults.isDefault()).isTrue();
    assertThat(defaults.isUsableBy(other)).isTrue();
    assertThat(defaults.isOwnedBy(other)).isFalse();

    assertThat(mine.isDefault()).isFalse();
    assertThat(mine.isUsableBy(owner)).isTrue();
    assertThat(mine.isUsableBy(other)).isFalse();
    assertThat(mine.isOwnedBy(null)).isFalse();
  }

  @Test
  void 음성은_한_번만_붙고_두_번째는_무시한다() {
    Phrase phrase = Phrase.createForUser(owner, PhraseCategory.DAILY, "물 좀 주세요.");

    assertThat(phrase.attachAudio("recordings/first.mp3")).isTrue();
    assertThat(phrase.attachAudio("recordings/second.mp3")).isFalse();

    assertThat(phrase.getAudioStorageKey()).isEqualTo("recordings/first.mp3");
  }

  @Test
  void 빈_음성_경로는_붙이지_않는다() {
    Phrase phrase = Phrase.createForUser(owner, PhraseCategory.DAILY, "물 좀 주세요.");

    assertThatThrownBy(() -> phrase.attachAudio(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThat(phrase.hasAudio()).isFalse();
  }

  @Test
  void 상한은_100개다() {
    assertThat(Phrase.hasReachedCustomLimit(99)).isFalse();
    assertThat(Phrase.hasReachedCustomLimit(100)).isTrue();
  }

  @Test
  void 카테고리_코드는_정확히_일치해야_한다() {
    assertThat(PhraseCategory.from("PHARMACY")).isEqualTo(PhraseCategory.PHARMACY);
    assertThatThrownBy(() -> PhraseCategory.from("pharmacy"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PhraseCategory.from("FOOD"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PhraseCategory.from(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptableText는_규칙을_통과하면_정리한_값을_아니면_빈_값을_준다() {
    assertThat(Phrase.acceptableText("  물 좀 주세요. ")).contains("물 좀 주세요.");
    assertThat(Phrase.acceptableText(" ")).isEmpty();
    assertThat(Phrase.acceptableText(null)).isEmpty();
    assertThat(Phrase.acceptableText("물\n주세요")).isEmpty();
    assertThat(Phrase.acceptableText("가".repeat(Phrase.MAX_TEXT_LENGTH + 1))).isEmpty();
  }
}
