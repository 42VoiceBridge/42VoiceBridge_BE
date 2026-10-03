-- 운영 DB 초기 스키마. 2026-10-03 develop의 JPA 엔티티 기준이다.
-- 이 파일은 운영 DB에 이미 적용되면 고칠 수 없다. 테이블·컬럼을 바꿀 때는 V2__설명.sql처럼 새 파일을 추가한다.
-- enum 필드는 MySQL enum 대신 VARCHAR로 둔다. 자바 enum에 값을 추가해도 스키마 검사(validate)는 통과하지만,
-- MySQL enum 컬럼이면 새 값을 처음 저장하는 순간 실패하기 때문이다.

CREATE TABLE users (
  id BINARY(16) NOT NULL,
  email VARCHAR(255) NULL,
  password VARCHAR(255) NULL,
  nickname VARCHAR(255) NULL,
  provider VARCHAR(255) NULL,
  provider_id VARCHAR(255) NULL,
  created_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_users_email UNIQUE (email),
  CONSTRAINT uk_users_provider UNIQUE (provider, provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 진단

CREATE TABLE sentences (
  id BINARY(16) NOT NULL,
  text VARCHAR(255) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE diagnosis_sessions (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NULL,
  status VARCHAR(255) NULL,
  created_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  INDEX idx_diagnosis_sessions_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE diagnosis_session_sentences (
  session_id BINARY(16) NOT NULL,
  sentence_order INT NOT NULL,
  sentence_ids BINARY(16) NULL,
  PRIMARY KEY (sentence_order, session_id),
  CONSTRAINT fk_diagnosis_session_sentences_session
    FOREIGN KEY (session_id) REFERENCES diagnosis_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recordings (
  id BINARY(16) NOT NULL,
  session_id BINARY(16) NULL,
  sentence_id BINARY(16) NULL,
  user_id BINARY(16) NULL,
  s3_path VARCHAR(255) NULL,
  status VARCHAR(255) NULL,
  recognized_text VARCHAR(1000) NULL,
  confidence DOUBLE NULL,
  created_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  INDEX idx_recordings_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE jamo_error_snapshots (
  user_id BINARY(16) NOT NULL,
  metric_version VARCHAR(255) NULL,
  min_support INT NOT NULL,
  sessions_used INT NOT NULL,
  pairs_used INT NOT NULL,
  computed_at DATETIME(6) NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE jamo_error_snapshot_tokens (
  user_id BINARY(16) NOT NULL,
  token_order INT NOT NULL,
  token VARCHAR(255) NULL,
  position VARCHAR(255) NULL,
  status VARCHAR(255) NULL,
  errors INT NULL,
  sample_count INT NULL,
  error_rate DOUBLE NULL,
  PRIMARY KEY (token_order, user_id),
  CONSTRAINT fk_jamo_error_snapshot_tokens_snapshot
    FOREIGN KEY (user_id) REFERENCES jamo_error_snapshots (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 추천 문장

CREATE TABLE shown_prompts (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NULL,
  prompt_id VARCHAR(255) NULL,
  text VARCHAR(500) NULL,
  strategy VARCHAR(255) NULL,
  strategy_version VARCHAR(255) NULL,
  seed BIGINT NOT NULL,
  pool_version VARCHAR(255) NULL,
  pool_sha256 VARCHAR(255) NULL,
  shown_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  INDEX idx_shown_prompts_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 개인화

CREATE TABLE personalization_recordings (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NULL,
  shown_prompt_id BINARY(16) NULL,
  prompt_id VARCHAR(255) NULL,
  prompt_text VARCHAR(500) NULL,
  storage_key VARCHAR(255) NULL,
  use_for_training BIT(1) NOT NULL,
  consent_version VARCHAR(255) NULL,
  consented_at DATETIME(6) NULL,
  source_format VARCHAR(255) NULL,
  source_codec VARCHAR(255) NULL,
  source_sample_rate INT NOT NULL,
  source_channels INT NOT NULL,
  sample_count INT NOT NULL,
  source_sha256 VARCHAR(255) NULL,
  wav_sha256 VARCHAR(255) NULL,
  normalization_version VARCHAR(255) NULL,
  status VARCHAR(255) NULL,
  created_at DATETIME(6) NULL,
  prompt_pool_version VARCHAR(255) NULL,
  reviewed_spoken_text VARCHAR(500) NULL,
  review_revision VARCHAR(255) NULL,
  reviewed_by BINARY(16) NULL,
  reviewed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  INDEX idx_personalization_recordings_user (user_id),
  INDEX idx_personalization_recordings_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE personalization_jobs (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NULL,
  active_slot VARCHAR(255) NULL,
  status VARCHAR(255) NULL,
  training_recording_count INT NOT NULL,
  model_version VARCHAR(255) NULL,
  model_artifact_path VARCHAR(255) NULL,
  failure_reason VARCHAR(255) NULL,
  started_at DATETIME(6) NULL,
  completed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  -- MySQL 고유 인덱스는 NULL을 여러 번 허용한다. 진행 중 job만 active_slot='ACTIVE'라 사용자당 하나로 제한된다.
  CONSTRAINT uk_personalization_job_active UNIQUE (user_id, active_slot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE personalization_adapters (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  job_id BINARY(16) NOT NULL,
  status VARCHAR(255) NOT NULL,
  active_user_id BINARY(16) NULL,
  model_version VARCHAR(255) NULL,
  base_revision VARCHAR(255) NULL,
  artifact_sha256 VARCHAR(255) NULL,
  training_recording_count INT NOT NULL,
  trained_at DATETIME(6) NULL,
  activated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_personalization_active_user UNIQUE (active_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 실사용 인식 · TTS

CREATE TABLE recognitions (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  recognized_text TEXT NOT NULL,
  confidence DOUBLE NULL,
  model_used VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_recognitions_user_id_created_at_id (user_id, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE confirmations (
  id BINARY(16) NOT NULL,
  recognition_id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  confirmed_text TEXT NOT NULL,
  valid BIT(1) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_confirmations_recognition_id (recognition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tts_requests (
  id BINARY(16) NOT NULL,
  confirmation_id BINARY(16) NOT NULL,
  idempotency_key BINARY(16) NOT NULL,
  status VARCHAR(255) NOT NULL,
  audio_url VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_tts_requests_idempotency_key UNIQUE (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
