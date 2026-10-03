package com.voicebridge.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.voicebridge.domain.diagnosis.JamoErrorSnapshot;
import com.voicebridge.domain.diagnosis.JamoErrorStat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JamoErrorSnapshotPersistenceAdapter.class)
class JamoErrorSnapshotPersistenceAdapterTest {

  @Autowired private JamoErrorSnapshotPersistenceAdapter adapter;
  @Autowired private TestEntityManager entityManager;

  private final UUID userId = UUID.randomUUID();

  private final JamoErrorStat enough = new JamoErrorStat("ㅈ", "INITIAL", 12, 20, 0.6, "OK");
  private final JamoErrorStat insufficient =
      new JamoErrorStat("ㅆ", "FINAL", 3, 7, null, "INSUFFICIENT_DATA");

  // 1차 캐시에서 꺼내는 게 아니라 실제 DB에서 다시 읽도록 비운다
  private JamoErrorSnapshot reload() {
    entityManager.flush();
    entityManager.clear();
    return adapter.findByUserId(userId).orElseThrow();
  }

  @Test
  void 저장한_스냅샷의_모든_값과_자모_순서가_그대로_복원된다() {
    adapter.save(
        JamoErrorSnapshot.create(userId, "jamo-err-v1", 20, 3, 15, List.of(enough, insufficient)));

    JamoErrorSnapshot found = reload();

    assertThat(found.getMetricVersion()).isEqualTo("jamo-err-v1");
    assertThat(found.getMinSupport()).isEqualTo(20);
    assertThat(found.getSessionsUsed()).isEqualTo(3);
    assertThat(found.getPairsUsed()).isEqualTo(15);
    assertThat(found.getComputedAt()).isNotNull();
    // 표본 부족의 null 오류율이 0.0으로 바뀌지 않아야 한다
    assertThat(found.getTokens()).containsExactly(enough, insufficient);
  }

  @Test
  void 같은_사용자로_다시_저장하면_자모_목록이_통째로_바뀐다() {
    adapter.save(
        JamoErrorSnapshot.create(userId, "jamo-err-v1", 20, 3, 15, List.of(enough, insufficient)));
    entityManager.flush();
    entityManager.clear();

    adapter.save(JamoErrorSnapshot.create(userId, "jamo-err-v1", 20, 4, 20, List.of(insufficient)));

    JamoErrorSnapshot found = reload();
    assertThat(found.getSessionsUsed()).isEqualTo(4);
    assertThat(found.getTokens()).containsExactly(insufficient);
  }

  @Test
  void 계산할_쌍이_없던_빈_스냅샷도_저장하고_복원한다() {
    adapter.save(JamoErrorSnapshot.empty(userId, 20, 2));

    JamoErrorSnapshot found = reload();

    assertThat(found.getMetricVersion()).isNull();
    assertThat(found.getSessionsUsed()).isEqualTo(2);
    assertThat(found.getTokens()).isEmpty();
  }

  @Test
  void 스냅샷이_없으면_빈_결과다() {
    assertThat(adapter.findByUserId(UUID.randomUUID())).isEmpty();
  }
}
