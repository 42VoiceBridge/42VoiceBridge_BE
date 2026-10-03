# 개인화 녹음 저장·운영

> 2026-10-03부터 운영 DB의 이 테이블은 Flyway `src/main/resources/db/migration/V1__init.sql`이 만든다. 아래 SQL은 기록용이며 운영에 직접 실행하지 않는다.

로컬은 `ddl-auto: update`로 `personalization_recordings`를 생성한다. 운영은 배포 전에 다음 스키마를 적용하고 `ddl-auto: validate`로 확인한다.

```sql
CREATE TABLE personalization_recordings (
  id BINARY(16) PRIMARY KEY,
  user_id BINARY(16) NOT NULL,
  shown_prompt_id BINARY(16) NOT NULL,
  prompt_id VARCHAR(255) NOT NULL,
  prompt_text VARCHAR(500) NOT NULL,
  storage_key VARCHAR(255) NOT NULL,
  use_for_training BOOLEAN NOT NULL,
  consent_version VARCHAR(255) NOT NULL,
  consented_at DATETIME(6) NOT NULL,
  source_format VARCHAR(255), source_codec VARCHAR(255),
  source_sample_rate INT NOT NULL, source_channels INT NOT NULL, sample_count INT NOT NULL,
  source_sha256 VARCHAR(255), wav_sha256 VARCHAR(255), normalization_version VARCHAR(255),
  status VARCHAR(255) NOT NULL, created_at DATETIME(6) NOT NULL,
  prompt_pool_version VARCHAR(255), reviewed_spoken_text VARCHAR(500),
  review_revision VARCHAR(255), reviewed_by BINARY(16), reviewed_at DATETIME(6),
  INDEX idx_personalization_recordings_user (user_id),
  INDEX idx_personalization_recordings_status_created (status, created_at)
);
```

기존 `personalization_recordings` 테이블이 있으면 새 nullable 열 5개를 추가한다. 기존 업로드에는 검토된 정답과 풀 버전이 없으므로 자동으로 학습 후보로 승격하지 않는다. 검토 주체·입력 절차와 풀 버전 출처는 AI 계약과 운영 절차 합의 후 연결한다.

```sql
ALTER TABLE personalization_recordings
  ADD COLUMN prompt_pool_version VARCHAR(255) NULL,
  ADD COLUMN reviewed_spoken_text VARCHAR(500) NULL,
  ADD COLUMN review_revision VARCHAR(255) NULL,
  ADD COLUMN reviewed_by BINARY(16) NULL,
  ADD COLUMN reviewed_at DATETIME(6) NULL;
```

`PREPARING` 행을 먼저 커밋한 뒤 `personalization/{recordingId}.wav`를 저장한다. 저장 성공 시 `UPLOADED`로 변경한다. 실패나 재시작 후 남은 준비 행은 5분 뒤 주기적 작업이 삭제한다. 삭제 실패는 행을 남기고 다음 주기에 재시도한다. 기본 보관 기간은 30일이며 `voicebridge.personalization.retention-days`로 변경할 수 있다. `DELETE /api/v1/personalization/recordings/{id}`는 즉시 학습 자격을 없애고 삭제를 시도한다. 민감 음성 삭제가 지연되면 로그의 녹음 ID로 상태와 저장소 객체를 확인한다.

업로드 예: multipart `metadata` JSON `{"shownPromptId":"...","promptId":"02-03-0001","storeAudio":true,"useForTraining":false}`와 `audioFile`을 전송한다. `shownPromptId`는 추천 응답에서 받는다.
