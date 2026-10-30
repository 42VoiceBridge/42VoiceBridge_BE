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
 * 운영 DB(MySQL)에서 문구 저장·조회와 (owner_id, category, text_sha256) 유니크를 확인한다. H2는 NULL이 섞인 유니크와 문자열 비교
 * 규칙(collation)이 MySQL과 달라서 실제 MySQL로 돌린다. 어댑터가 각자 트랜잭션을 열고 닫아야 유니크 위반 번역을 볼 수 있으므로 테스트 트랜잭션은 끈다.
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
    Phrase phrase = Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요.");
    adapter.save(phrase);

    Phrase found = adapter.findById(phrase.getId()).orElseThrow();

    assertThat(found.getOwnerId()).isEqualTo(me);
    assertThat(found.getCategory()).isEqualTo(PhraseCategory.DAILY);
    assertThat(found.getText()).isEqualTo("물 좀 주세요.");
    assertThat(found.hasAudio()).isFalse();
  }

  @Test
  void 음성_key를_붙여_다시_저장하면_반영된다() {
    Phrase phrase = adapter.save(Phrase.createDefault(PhraseCategory.HOSPITAL, "접수하고 싶어요."));
    phrase.attachAudio("recordings/0b6f1c2e-1111-4222-8333-944445555666.mp3");
    adapter.save(phrase);

    assertThat(adapter.findById(phrase.getId()).orElseThrow().getAudioStorageKey())
        .isEqualTo("recordings/0b6f1c2e-1111-4222-8333-944445555666.mp3");
  }

  @Test
  void 목록에는_기본_문구와_내_문구만_있고_다른_사람_문구는_없다() {
    Phrase defaults = adapter.save(Phrase.createDefault(PhraseCategory.HOSPITAL, "접수하고 싶어요."));
    Phrase mine = adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요."));
    adapter.save(Phrase.createForUser(other, PhraseCategory.DAILY, "다른 사람 문구"));

    assertThat(adapter.findAllVisibleTo(me, null))
        .extracting(Phrase::getId)
        .containsExactly(defaults.getId(), mine.getId());
  }

  @Test
  void 카테고리를_주면_그_카테고리만_DB_조건으로_거른다() {
    adapter.save(Phrase.createDefault(PhraseCategory.HOSPITAL, "접수하고 싶어요."));
    Phrase restaurant = adapter.save(Phrase.createDefault(PhraseCategory.RESTAURANT, "메뉴판 주세요."));
    Phrase mine = adapter.save(Phrase.createForUser(me, PhraseCategory.RESTAURANT, "포장이요."));
    adapter.save(Phrase.createForUser(other, PhraseCategory.RESTAURANT, "다른 사람 문구"));

    assertThat(adapter.findAllVisibleTo(me, PhraseCategory.RESTAURANT))
        .extracting(Phrase::getId)
        .containsExactly(restaurant.getId(), mine.getId());
  }

  @Test
  void 같은_사용자가_같은_카테고리에_같은_문구를_두_번_저장하면_중복으로_번역한다() {
    adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요."));

    assertThatThrownBy(
            () -> adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요.")))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.DUPLICATE_PHRASE);
  }

  @Test
  void 카테고리가_다르면_같은_문구도_저장된다() {
    adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요."));
    adapter.save(Phrase.createForUser(me, PhraseCategory.RESTAURANT, "물 좀 주세요."));

    assertThat(adapter.countCustomByOwner(me)).isEqualTo(2);
  }

  @Test
  void 대소문자만_다른_문구는_다른_문구로_본다() {
    // MySQL collation(ai_ci)은 "OK"와 "ok"를 같다고 비교한다. 해시에 유니크를 걸었으므로 둘 다 저장돼야 한다.
    adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "OK"));
    adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "ok"));

    assertThat(adapter.countCustomByOwner(me)).isEqualTo(2);
    assertThat(adapter.existsOwnText(me, PhraseCategory.DAILY, "OK")).isTrue();
    assertThat(adapter.existsOwnText(me, PhraseCategory.DAILY, "Ok")).isFalse();
  }

  @Test
  void 다른_사용자는_같은_문구를_가질_수_있다() {
    adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요."));
    adapter.save(Phrase.createForUser(other, PhraseCategory.DAILY, "물 좀 주세요."));

    assertThat(adapter.countCustomByOwner(me)).isEqualTo(1);
    assertThat(adapter.countCustomByOwner(other)).isEqualTo(1);
  }

  @Test
  void 중복_확인은_내_문구만_같은_카테고리에서_본다() {
    adapter.save(Phrase.createDefault(PhraseCategory.RESTAURANT, "물 좀 주세요."));
    adapter.save(Phrase.createForUser(other, PhraseCategory.DAILY, "다른 사람 문구"));
    adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "내 문구"));

    // 기본 문구와 같은 텍스트는 중복이 아니다(계획서 4.3: 내 문구끼리만 막는다)
    assertThat(adapter.existsOwnText(me, PhraseCategory.RESTAURANT, "물 좀 주세요.")).isFalse();
    assertThat(adapter.existsOwnText(me, PhraseCategory.DAILY, "다른 사람 문구")).isFalse();
    assertThat(adapter.existsOwnText(me, PhraseCategory.DAILY, "내 문구")).isTrue();
    assertThat(adapter.existsOwnText(me, PhraseCategory.HOSPITAL, "내 문구")).isFalse();
  }

  @Test
  void 개수는_내가_추가한_문구만_센다() {
    adapter.save(Phrase.createDefault(PhraseCategory.HOSPITAL, "접수하고 싶어요."));
    adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "하나"));
    adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "둘"));

    assertThat(adapter.countCustomByOwner(me)).isEqualTo(2);
  }

  @Test
  void 삭제하면_다시_찾을_수_없다() {
    Phrase phrase = adapter.save(Phrase.createForUser(me, PhraseCategory.DAILY, "물 좀 주세요."));

    adapter.deleteById(phrase.getId());

    assertThat(adapter.findById(phrase.getId())).isEmpty();
  }
}
