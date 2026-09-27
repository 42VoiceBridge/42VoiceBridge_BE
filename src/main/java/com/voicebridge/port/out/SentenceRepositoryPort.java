package com.voicebridge.port.out;

import com.voicebridge.domain.diagnosis.Sentence;
import java.util.List;
import java.util.UUID;

public interface SentenceRepositoryPort {

  /** 진단 세션 시작 시 낭독시킬 문장을 count개 선택한다. 선정 로직(무작위/고정 등)은 어댑터가 결정. */
  List<Sentence> findDiagnosisSentences(int count);

  List<Sentence> findAllByIds(List<UUID> ids);
}
