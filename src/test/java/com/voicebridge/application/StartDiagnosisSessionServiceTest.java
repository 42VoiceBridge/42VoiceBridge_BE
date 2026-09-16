package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.voicebridge.common.exception.CustomException;
import com.voicebridge.domain.diagnosis.Sentence;
import com.voicebridge.port.out.DiagnosisSessionRepositoryPort;
import com.voicebridge.port.out.SentenceRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 6장 테스트 컨벤션(@ExtendWith(MockitoExtension.class) + 명시적 생성자 주입) 예시. Repository/영속성 어댑터가 아니라
 * application 계층 오케스트레이션 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class StartDiagnosisSessionServiceTest {

  @Mock private SentenceRepositoryPort sentenceRepositoryPort;

  @Mock private DiagnosisSessionRepositoryPort diagnosisSessionRepositoryPort;

  private StartDiagnosisSessionService service;

  @BeforeEach
  void setUp() {
    service =
        new StartDiagnosisSessionService(sentenceRepositoryPort, diagnosisSessionRepositoryPort);
  }

  @Test
  void 문장이_준비돼있으면_진단_세션을_시작한다() {
    UUID userId = UUID.randomUUID();
    List<Sentence> sentences =
        List.of(
            new Sentence(UUID.randomUUID(), "오늘 날씨가 좋습니다."),
            new Sentence(UUID.randomUUID(), "물을 마시고 싶습니다."));
    when(sentenceRepositoryPort.findDiagnosisSentences(anyInt())).thenReturn(sentences);
    when(diagnosisSessionRepositoryPort.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.start(userId);

    assertThat(result.status()).isEqualTo("IN_PROGRESS");
    assertThat(result.sentences()).hasSize(2);
  }

  @Test
  void 문장이_없으면_예외를_던진다() {
    UUID userId = UUID.randomUUID();
    when(sentenceRepositoryPort.findDiagnosisSentences(anyInt())).thenReturn(List.of());

    assertThatThrownBy(() -> service.start(userId)).isInstanceOf(CustomException.class);
  }
}
