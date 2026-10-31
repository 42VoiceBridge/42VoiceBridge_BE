-- 지우지 못한 저장소 파일(S3) 재시도 큐. 음성 데이터 초기화·회원 탈퇴 때 DB 행은 지워졌는데 파일 삭제가 실패하면 여기 넣고,
-- 스케줄러가 주기적으로 다시 지운다(개인정보처리방침: 일시 오류로 남은 파일도 자동 재시도로 삭제).
-- storage_key가 기본 키라 같은 파일이 두 번 들어가지 않는다. key는 무작위 UUID라 누구의 파일인지 담지 않는다.
-- created_at 순서로 꺼내므로 그 인덱스를 둔다.

CREATE TABLE storage_deletion_queue (
  storage_key VARCHAR(255) NOT NULL,
  attempts INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  last_attempt_at DATETIME(6) NULL,
  PRIMARY KEY (storage_key),
  INDEX idx_storage_deletion_queue_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
