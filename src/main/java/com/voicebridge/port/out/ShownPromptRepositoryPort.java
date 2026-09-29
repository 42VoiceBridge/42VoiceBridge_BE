package com.voicebridge.port.out;

import com.voicebridge.domain.recommendation.ShownPrompt;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ShownPromptRepositoryPort {

  List<ShownPrompt> saveAll(List<ShownPrompt> prompts);

  /** 이 사용자에게 이미 보여준 문장 ID. 다음 추천에서 같은 문장이 다시 나오지 않게 뺀다. */
  Set<String> findPromptIdsByUserId(UUID userId);
}
