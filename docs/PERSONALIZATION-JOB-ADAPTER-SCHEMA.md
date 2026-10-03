# 개인화 job과 adapter 저장 스키마

운영 프로필은 `ddl-auto: validate`다. 배포 전에 운영 MySQL에 다음 DDL을 적용한다. `personalization_jobs`가 이미 있다면 첫 `CREATE TABLE`을 건너뛰고 아래의 기존 테이블 변경문을 적용한다. 동일 사용자 `PENDING`/`IN_PROGRESS` 행이 둘 이상 있으면 먼저 이력을 정리해야 고유 제약을 추가할 수 있다.

```sql
CREATE TABLE personalization_jobs (
  id BINARY(16) PRIMARY KEY,
  user_id BINARY(16),
  active_slot VARCHAR(255),
  status VARCHAR(255),
  training_recording_count INT NOT NULL,
  model_version VARCHAR(255),
  model_artifact_path VARCHAR(255),
  failure_reason VARCHAR(255),
  started_at DATETIME(6),
  completed_at DATETIME(6),
  CONSTRAINT uk_personalization_job_active UNIQUE (user_id, active_slot)
);

-- 기존 personalization_jobs 테이블에만 적용
ALTER TABLE personalization_jobs ADD COLUMN active_slot VARCHAR(255) NULL;
UPDATE personalization_jobs SET active_slot = 'ACTIVE'
WHERE status IN ('PENDING', 'IN_PROGRESS');
ALTER TABLE personalization_jobs
  ADD CONSTRAINT uk_personalization_job_active UNIQUE (user_id, active_slot);

CREATE TABLE personalization_adapters (
  id BINARY(16) PRIMARY KEY,
  user_id BINARY(16) NOT NULL,
  job_id BINARY(16) NOT NULL,
  status VARCHAR(255) NOT NULL,
  active_user_id BINARY(16) NULL,
  model_version VARCHAR(255),
  base_revision VARCHAR(255),
  artifact_sha256 VARCHAR(255),
  training_recording_count INT NOT NULL,
  trained_at DATETIME(6),
  activated_at DATETIME(6),
  CONSTRAINT uk_personalization_active_user UNIQUE (active_user_id)
);
```

MySQL의 고유 인덱스는 `NULL`을 여러 번 허용하므로 완료/실패 job 이력과 비활성 adapter는 여러 개 저장할 수 있다. `active_slot='ACTIVE'`는 `PENDING` 또는 `IN_PROGRESS`만 사용한다. 새 job 제출 경로가 열리기 전까지 활성 adapter를 생성하는 코드가 없으며, `ACTIVE`는 실제 설치·serving 확인 후에만 기록해야 한다.
