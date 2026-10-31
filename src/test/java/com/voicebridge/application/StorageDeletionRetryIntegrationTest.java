package com.voicebridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.voicebridge.adapter.out.persistence.StorageDeletionJpaRepository;
import com.voicebridge.domain.storage.StorageDeletionPolicy;
import com.voicebridge.port.in.RetryStorageDeletionUseCase;
import com.voicebridge.port.out.StorageDeletionQueuePort;
import com.voicebridge.port.out.StorageDeletionQueuePort.QueuedDeletion;
import com.voicebridge.port.out.StoragePort;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 큐는 실제 DB(테스트 H2), 저장소(S3)만 가짜로 둔다. 스케줄러는 테스트 설정에서 꺼 두고 재시도 서비스를 직접 부른다.
@SpringBootTest
class StorageDeletionRetryIntegrationTest {

  @Autowired RetryStorageDeletionUseCase retry;
  @Autowired StorageDeletionQueuePort queue;
  @Autowired StorageDeletionJpaRepository repository;
  @MockitoBean StoragePort storage;

  @BeforeEach
  void setUp() {
    repository.deleteAllInBatch();
  }

  @Test
  void 같은_파일은_한_번만_큐에_들어간다() {
    queue.enqueue("recordings/a.mp3");
    queue.enqueue("recordings/a.mp3");

    assertThat(queue.findOldest(10)).containsExactly(new QueuedDeletion("recordings/a.mp3", 0));
  }

  @Test
  void 지우면_큐에서_빠지고_실패하면_시도_횟수가_올라_남는다() {
    queue.enqueue("recordings/ok.mp3");
    queue.enqueue("recordings/fail.mp3");
    doThrow(new IllegalStateException("s3 down")).when(storage).delete("recordings/fail.mp3");

    assertThat(retry.retryPending()).isEqualTo(1);

    verify(storage).delete("recordings/ok.mp3");
    assertThat(queue.findOldest(10)).containsExactly(new QueuedDeletion("recordings/fail.mp3", 1));
  }

  @Test
  void 다음_주기에_성공하면_큐가_빈다() {
    queue.enqueue("recordings/fail.mp3");
    doThrow(new IllegalStateException("s3 down")).when(storage).delete("recordings/fail.mp3");
    retry.retryPending();
    org.mockito.Mockito.reset(storage);

    assertThat(retry.retryPending()).isEqualTo(1);
    assertThat(queue.findOldest(10)).isEmpty();
  }

  @Test
  void 한_번에_정해진_개수까지만_처리하고_오래된_것부터_꺼낸다() {
    IntStream.range(0, StorageDeletionPolicy.RETRY_BATCH_SIZE + 5)
        .forEach(i -> queue.enqueue("recordings/" + i + ".mp3"));

    assertThat(retry.retryPending()).isEqualTo(StorageDeletionPolicy.RETRY_BATCH_SIZE);
    assertThat(queue.findOldest(10)).hasSize(5);
  }
}
