package com.voicebridge.port.out;

import com.voicebridge.domain.recommendation.ShownPrompt;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ShownPromptRepositoryPort {

  List<ShownPrompt> saveAll(List<ShownPrompt> prompts);

  /**
   * 이 사용자에게 이미 제안한 문장 ID. 다음 추천에서 같은 문장이 다시 나오지 않게 뺀다. 제안만 하고 읽지 않은 문장(예: 새로고침)도 빠지는데, 녹음 여부를 알 수
   * 없는 지금은 구분할 방법이 없다. 녹음 안 한 제안 문장을 다시 주는 이어하기는 개인화 녹음(FR-7)이 생기면 정한다.
   */
  Set<String> findPromptIdsByUserId(UUID userId);
}
