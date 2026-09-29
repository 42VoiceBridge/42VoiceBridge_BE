package com.voicebridge.adapter.out.persistence;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.common.exception.ErrorCode;
import com.voicebridge.domain.recommendation.ShownPrompt;
import com.voicebridge.port.out.ShownPromptRepositoryPort;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** 현재는 유일 제약이 없어 발생 시나리오가 없는 방어 코드 — 제약 추가 시를 대비한 것. */
@Component
@RequiredArgsConstructor
public class ShownPromptPersistenceAdapter implements ShownPromptRepositoryPort {

  private final ShownPromptJpaRepository jpaRepository;

  @Override
  public List<ShownPrompt> saveAll(List<ShownPrompt> prompts) {
    try {
      return jpaRepository
          .saveAll(prompts.stream().map(ShownPromptPersistenceAdapter::toEntity).toList())
          .stream()
          .map(ShownPromptPersistenceAdapter::toDomain)
          .toList();
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "추천 문장 기록을 저장하지 못했습니다.");
    }
  }

  @Override
  public Set<String> findPromptIdsByUserId(UUID userId) {
    return new HashSet<>(jpaRepository.findPromptIdsByUserId(userId));
  }

  private static ShownPromptJpaEntity toEntity(ShownPrompt prompt) {
    return ShownPromptJpaEntity.builder()
        .id(prompt.getId())
        .userId(prompt.getUserId())
        .promptId(prompt.getPromptId())
        .text(prompt.getText())
        .strategy(prompt.getStrategy())
        .strategyVersion(prompt.getStrategyVersion())
        .seed(prompt.getSeed())
        .shownAt(prompt.getShownAt())
        .build();
  }

  private static ShownPrompt toDomain(ShownPromptJpaEntity entity) {
    return ShownPrompt.reconstitute(
        entity.getId(),
        entity.getUserId(),
        entity.getPromptId(),
        entity.getText(),
        entity.getStrategy(),
        entity.getStrategyVersion(),
        entity.getSeed(),
        entity.getShownAt());
  }
}
