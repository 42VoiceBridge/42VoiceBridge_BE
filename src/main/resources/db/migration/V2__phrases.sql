-- 상황별 자주 쓰는 문구(2026-10 방향 전환). owner_id가 NULL이면 모든 사용자에게 보이는 기본 문구, 있으면 그 사용자가 직접 추가한 문구다.
-- 기본 문구 데이터는 이 파일이 아니라 PhraseSeeder가 넣는다(바이너리 UUID 리터럴이 Hibernate 인코딩과 어긋날 수 있다).
-- (owner_id, category, text_sha256) 유니크: 같은 사용자의 같은 카테고리 같은 문구를 막는다. text가 아니라 해시에 거는 이유는
--   이 테이블의 collation(utf8mb4_0900_ai_ci)이 대소문자·악센트를 무시하고 비교하기 때문이다. 해시는 글자가 정확히 같을 때만 같다.
--   인덱스가 (owner_id, category)로 시작해 "기본 + 내 문구(+ 카테고리)" 목록 조회와 개수 세기에도 쓰이므로 같은 앞부분의 인덱스를 따로 두지 않는다.
-- text VARCHAR(200): 도메인 정책 상한은 100자다. 정책을 올려도 스키마를 바꾸지 않도록 넉넉하게 둔다.
-- audio_storage_key: 처음 말하기를 누를 때 합성해 저장한 음성의 object key. 재생 URL은 저장하지 않고 조회마다 만든다.
-- 2026-10-30: 운영 DB에 적용되기 전에 계획서(PHRASE-TTS-PIVOT-PLAN 4.3)에 맞춰 이 파일을 고쳤다. 운영에 적용된 뒤에는 고치지 말고 V3를 추가한다.

CREATE TABLE phrases (
  id BINARY(16) NOT NULL,
  owner_id BINARY(16) NULL,
  category VARCHAR(255) NOT NULL,
  text VARCHAR(200) NOT NULL,
  text_sha256 CHAR(64) NOT NULL,
  audio_storage_key VARCHAR(255) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_phrases_owner_category_text UNIQUE (owner_id, category, text_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
