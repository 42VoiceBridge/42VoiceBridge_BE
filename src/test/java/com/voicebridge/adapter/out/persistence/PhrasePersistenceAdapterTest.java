package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.phrase.Phrase;
import com.voicebridge.domain.phrase.PhraseCategory;
import com.voicebridge.support.MySqlContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 DB(MySQL)에서 문구 저장·조회와 (owner_id, text) 유니크를 확인한다. H2는 NULL이 섞인 유니크와 문자열 비교 규칙(collation)이
 * MySQL과 달라서 실제 MySQL로 돌린다. 어댑터가 각자 트랜잭션을 열고 닫아야 유니크 위반 번역을 볼 수 있으므로 테스트 트랜잭션은 끈다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PhrasePersistenceAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PhrasePersistenceAdapterTest extends MySqlContainerTest {

  @Autowired PhrasePersistenceAdapter adapter;
  @Autowired PhraseJpaRepository jpaRepository;

  private final UUID me = UUID.randomUUID();
  private final UUID other = UUID.randomUUID();

  // 공용 컨테이너라 다른 테스트가 남긴 행이 있을 수 있다. 시작할 때 비워 기본 문구 개수를 정확히 센다.
  @BeforeEach
  void clean() {
    jpaRepository.deleteAllInBatch();
  }

  @Test
  void 저장한_문구를_그대로_읽는다() {
    Phrase phrase = Phrase.createCustom(me, PhraseCategory.DAILY, "물 좀 주세요.");
    adapter.save(phrase);

    Phrase found = adapter.findById(phrase.getId()).orElseThrow();

    assertThat(found.getOwnerId()).isEqualTo(me);
    assertThat(found.getCategory()).isEqualTo(PhraseCategory.DAILY);
    assertThat(found.getText()).isEqualTo("물 좀 주세요.");
    assertThat(found.hasAudio()).isFalse();
  }

  @Test
  void 음성_key를_붙여_다시_저장하면_반영된다() {
    Phrase phrase = adapter.save(Phrase.createDefault(PhraseCategory.GREETING, "안녕하세요."));
    phrase.attachAudio("recordings/0b6f1c2e-1111-4222-8333-944445555666.mp3");
    adapter.save(phrase);

    assertThat(adapter.findById(phrase.getId()).orElseThrow().getAudioStorageKey())
        .isEqualTo("recordings/0b6f1c2e-1111-4222-8333-944445555666.mp3");
  }

  @Test
  void 목록에는_기본_문구와_내_문구만_있고_다른_사람_문구는_없다() {
    Phrase defaults = adapter.save(Phrase.createDefault(PhraseCategory.GREETING, "안녕하세요."));
    Phrase mine = adapter.save(Phrase.createCustom(me, PhraseCategory.DAILY, "물 좀 주세요."));
    adapter.save(Phrase.createCustom(other, PhraseCategory.DAILY, "다른 사람 문구"));

    assertThat(adapter.findAllVisibleTo(me))
        .extracting(Phrase::getId)
        .containsExactly(defaults.getId(), mine.getId());
  }

  @Test
  void 같은_사용자가_같은_문구를_두_번_저장하면_중복으로_번역한다() {
    adapter.save(Phrase.createCustom(me, PhraseCategory.DAILY, "물 좀 주세요."));

    assertThatThrownBy(
            () -> adapter.save(Phrase.createCustom(me, PhraseCategory.HEALTH, "물 좀 주세요.")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PHRASE_DUPLICATED);
  }

  @Test
  void 다른_사용자는_같은_문구를_가질_수_있다() {
    adapter.save(Phrase.createCustom(me, PhraseCategory.DAILY, "물 좀 주세요."));
    adapter.save(Phrase.createCustom(other, PhraseCategory.DAILY, "물 좀 주세요."));

    assertThat(adapter.countCustomByOwner(me)).isEqualTo(1);
    assertThat(adapter.countCustomByOwner(other)).isEqualTo(1);
  }

  @Test
  void 같은_텍스트는_기본_문구나_내_문구에_있을_때만_있다고_본다() {
    adapter.save(Phrase.createDefault(PhraseCategory.GREETING, "안녕하세요."));
    adapter.save(Phrase.createCustom(other, PhraseCategory.DAILY, "다른 사람 문구"));

    assertThat(adapter.existsVisibleText(me, "안녕하세요.")).isTrue();
    assertThat(adapter.existsVisibleText(me, "다른 사람 문구")).isFalse();
    assertThat(adapter.existsVisibleText(other, "다른 사람 문구")).isTrue();
  }

  @Test
  void 개수는_내가_추가한_문구만_센다() {
    adapter.save(Phrase.createDefault(PhraseCategory.GREETING, "안녕하세요."));
    adapter.save(Phrase.createCustom(me, PhraseCategory.DAILY, "하나"));
    adapter.save(Phrase.createCustom(me, PhraseCategory.DAILY, "둘"));

    assertThat(adapter.countCustomByOwner(me)).isEqualTo(2);
  }

  @Test
  void 삭제하면_다시_찾을_수_없다() {
    Phrase phrase = adapter.save(Phrase.createCustom(me, PhraseCategory.DAILY, "물 좀 주세요."));

    adapter.deleteById(phrase.getId());

    assertThat(adapter.findById(phrase.getId())).isEmpty();
  }
}
