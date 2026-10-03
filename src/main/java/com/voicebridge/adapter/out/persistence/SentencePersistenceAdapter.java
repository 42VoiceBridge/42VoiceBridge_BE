package com.voicebridge.adapter.out.persistence;

import com.voicebridge.domain.diagnosis.Sentence;
import com.voicebridge.port.out.SentenceRepositoryPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SentencePersistenceAdapter implements SentenceRepositoryPort {

  private final SentenceJpaRepository jpaRepository;

  @Override
  public List<Sentence> findDiagnosisSentences(int count) {
    return jpaRepository.findRandom(count).stream()
        .map(entity -> new Sentence(entity.getId(), entity.getText()))
        .toList();
  }

  @Override
  public List<Sentence> findAllByIds(List<UUID> ids) {
    return jpaRepository.findAllById(ids).stream()
        .map(entity -> new Sentence(entity.getId(), entity.getText()))
        .toList();
  }
}
