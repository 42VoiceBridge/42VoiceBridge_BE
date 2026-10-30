-- 자주 쓰는 문구(2026-10 방향 전환). owner_id가 NULL이면 모든 사용자에게 보이는 기본 문구, 있으면 그 사용자가 직접 추가한 문구다.
-- 기본 문구 데이터는 이 파일이 아니라 PhraseSeeder가 넣는다(바이너리 UUID 리터럴이 Hibernate 인코딩과 어긋날 수 있다).
-- (owner_id, text) 유니크: 같은 사용자의 같은 문구를 막고, owner_id로 시작해 "기본 + 내 문구" 목록 조회와 개수 세기에도 쓰인다.
-- audio_storage_key: 처음 말하기를 누를 때 합성해 저장한 음성의 object key. 재생 URL은 저장하지 않고 조회마다 만든다.

CREATE TABLE phrases (
  id BINARY(16) NOT NULL,
  owner_id BINARY(16) NULL,
  category VARCHAR(255) NOT NULL,
  text VARCHAR(100) NOT NULL,
  audio_storage_key VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_phrases_owner_text UNIQUE (owner_id, text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
