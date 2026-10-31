-- 실사용 인식(5.1)의 AI 인식 원문. recognized_text는 화면에 보여 주는 문장이라, 인식 보정이 켜져 있으면 LLM 보정본이 들어간다.
-- 원문은 화면에 내보내지 않고 AI 팀 분석·문제 추적용으로만 남긴다. 이 마이그레이션 이전 행은 NULL이다.

ALTER TABLE recognitions ADD COLUMN raw_text TEXT NULL;
