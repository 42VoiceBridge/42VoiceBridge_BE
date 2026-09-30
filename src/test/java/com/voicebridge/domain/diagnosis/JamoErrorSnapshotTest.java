package com.voicebridge.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JamoErrorSnapshotTest {

  private final UUID userId = UUID.randomUUID();
  private final JamoErrorStat stat = new JamoErrorStat("ㅈ", "INITIAL", 12, 20, 0.6, "OK");

  private JamoErrorSnapshot snapshotOf(int sessionsUsed, int minSupport) {
    return JamoErrorSnapshot.create(
        userId, "jamo-err-v1", minSupport, sessionsUsed, 15, List.of(stat));
  }

  @Test
  void 분석한_세션_수가_그대로면_다시_계산하지_않는다() {
    assertThat(snapshotOf(3, 20).isStale(3, 20)).isFalse();
  }

  @Test
  void 분석한_세션이_늘었으면_다시_계산한다() {
    assertThat(snapshotOf(3, 20).isStale(4, 20)).isTrue();
  }

  @Test
  void 기준_표본_수가_바뀌었으면_다시_계산한다() {
    assertThat(snapshotOf(3, 20).isStale(3, 10)).isTrue();
  }

  @Test
  void 계산할_쌍이_없으면_계산_버전_없이_빈_스냅샷을_만든다() {
    JamoErrorSnapshot empty = JamoErrorSnapshot.empty(userId, 20, 0);

    assertThat(empty.getMetricVersion()).isNull();
    assertThat(empty.getPairsUsed()).isZero();
    assertThat(empty.getTokens()).isEmpty();
    assertThat(empty.isStale(0, 20)).isFalse();
    assertThat(empty.isStale(1, 20)).isTrue();
  }

  @Test
  void 만든_뒤_원본_목록을_바꿔도_스냅샷은_바뀌지_않는다() {
    List<JamoErrorStat> source = new ArrayList<>(List.of(stat));
    JamoErrorSnapshot snapshot = JamoErrorSnapshot.create(userId, "jamo-err-v1", 20, 1, 5, source);

    source.clear();

    assertThat(snapshot.getTokens()).containsExactly(stat);
  }

  @Test
  void 계산_버전이_없으면_계산_결과로_만들_수_없다() {
    assertThatThrownBy(() -> JamoErrorSnapshot.create(userId, null, 20, 1, 5, List.of(stat)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
