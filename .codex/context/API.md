# API 명세서

---
상태: 진행 중
생성일시: 2026년 9월 14일 오후 4:19

# AI 구음장애 보조 서비스 — API 명세서

> 문서 버전: v1.1 (2026-09-30 갱신, 변경 이력은 9장)
>

---

## 0. 공통 규격 (Conventions)

### 0.1 Base URL

```
https://{host}/api/v1
```

### 0.2 인증

- 방식: JWT Bearer Token
- 헤더: `Authorization: Bearer {accessToken}`
- 인증이 필요 없는 API: `POST /auth/signup`, `POST /auth/login`, `POST /auth/refresh`

### 0.3 공통 응답 포맷

성공:

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

실패:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "STRING_ERROR_CODE",
    "message": "사람이 읽을 수 있는 에러 메시지"
  }
}
```

### 0.4 페이지네이션 (목록 조회 공통)

요청 쿼리파라미터: `page`(0부터 시작, 기본 0), `size`(기본 20)

응답 공통 포맷:

```json
{
  "success": true,
  "data": {
    "content": [ ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

### 0.5 공통 HTTP 상태 코드

| 코드 | 의미 |
| --- | --- |
| 200 | 성공 (조회/수정) |
| 201 | 생성 성공 |
| 202 | 비동기 작업 접수(예: 진단 녹음 업로드, 개인화 학습 트리거, TTS 요청) |
| 400 | 요청 형식 오류(검증 실패) |
| 401 | 인증 실패/토큰 만료 |
| 403 | 권한 없음(타인 데이터 접근 등) |
| 404 | 리소스 없음 |
| 409 | 상태 충돌(예: 이미 진행중인 학습 job에 재요청) |
| 422 | 비즈니스 규칙 위반(예: 최소 녹음 개수 미충족) |
| 500 | 서버 내부 오류 |
| 502/503 | AI 추론/학습 서버 장애 (기본 모델 폴백 여부는 4.2절 참고) |
| 503 | 음성 변환 슬롯 대기 또는 처리 시간 초과로 일시적 처리 불가 |

### 0.6 공통 에러 코드

| code | 상황 |
| --- | --- |
| `AUTH_INVALID_CREDENTIALS` | 로그인 실패 |
| `AUTH_TOKEN_EXPIRED` | 액세스 토큰 만료 |
| `VALIDATION_FAILED` | 요청 검증 실패. 필수 파라미터·파일이 빠지면 메시지에 빠진 필드명이 담긴다(예: `필수 요청 값이 없습니다: audioFile`) |
| `RESOURCE_NOT_FOUND` | 대상 리소스 없음 |
| `FORBIDDEN_ACCESS` | 본인 소유가 아닌 리소스 접근 |
| `INVALID_STATE_TRANSITION` | 상태 머신 규칙 위반 (예: 분석이 끝난 진단 세션에 녹음 추가) |
| `TRAINING_UNAVAILABLE` | 온라인 학습 계약과 AI job 조회 경로가 없어 학습 요청을 접수할 수 없음 (HTTP 503) |
| `INSUFFICIENT_RECORDINGS` | 개인화 학습에 필요한 최소 녹음 수 미충족 |
| `AI_INFERENCE_UNAVAILABLE` | AI 서버를 쓸 수 없음 (장애, 타임아웃, 계약과 다른 응답, 서버에 문장 풀 파일 없음) |
| `AUDIO_TOO_SHORT` | 진단 녹음이 0.3초 미만 (HTTP 400). 조금 더 길게 다시 녹음 |
| `AUDIO_TOO_LONG` | 진단 녹음이 30초 초과 (HTTP 400). 짧게 끊어서 다시 녹음 |
| `AUDIO_INVALID` | 진단 녹음 파일이 손상됐거나 지원하지 않는 형식 (HTTP 400). 다시 녹음 |
| `AUDIO_PROCESSING_UNAVAILABLE` | 음성 변환 슬롯 대기 초과 또는 변환 처리 시간 초과로 일시적으로 처리할 수 없음 (HTTP 503). 실사용 인식·진단 업로드·개인화 업로드의 변환 경로에 공통 적용 |

---

## 1. 인증 / 회원 (담당: 백엔드 개발자 A)

### 1.1 회원가입

`POST /auth/signup`

Request Body:

```json
{
  "email": "user@example.com",
  "password": "string (8자 이상)",
  "nickname": "string"
}
```

Response (201):

```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "email": "user@example.com",
    "nickname": "string"
  }
}
```

에러: `VALIDATION_FAILED`(400), 이메일 중복 시 `409`

---

### 1.2 로그인

`POST /auth/login`

Request:

```json
{ "email": "user@example.com", "password": "string" }
```

Response (200):

```json
{
  "success": true,
  "data": {
    "accessToken": "jwt",
    "refreshToken": "jwt",
    "expiresIn": 3600
  }
}
```

에러: `AUTH_INVALID_CREDENTIALS`(401)

---

### 1.3 토큰 재발급

`POST /auth/refresh`

Request: `{ "refreshToken": "jwt" }`
Response (200): `{ "accessToken": "jwt", "expiresIn": 3600 }`

---

### 1.4 내 정보 조회

`GET /users/me` (인증 필요)

Response (200):

```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "email": "user@example.com",
    "nickname": "string",
    "hasPersonalizedModel": false,
    "createdAt": "2026-09-14T09:00:00Z"
  }
}
```

---

## 2. 진단 세션 (담당: 백엔드 개발자 A)

### 2.1 진단 세션 시작

`POST /diagnosis-sessions` (인증 필요)

Request: `{}` (바디 없음, 서버가 낭독 문장 세트를 랜덤/고정 로직으로 구성)

Response (201):

```json
{
  "success": true,
  "data": {
    "sessionId": "uuid",
    "status": "IN_PROGRESS",
    "sentences": [
      { "sentenceId": "s-001", "text": "오늘 날씨가 좋습니다." },
      { "sentenceId": "s-002", "text": "물을 마시고 싶습니다." }
    ],
    "createdAt": "2026-09-14T09:00:00Z"
  }
}
```

### 2.2 진단 세션 조회

`GET /diagnosis-sessions/{sessionId}` (인증 필요, 본인 소유만)

Response (200):

```json
{
  "success": true,
  "data": {
    "sessionId": "uuid",
    "status": "IN_PROGRESS",
    "sentences": [
      { "sentenceId": "uuid", "text": "오늘 날씨가 좋습니다.", "recordingId": "uuid", "recordingStatus": "DONE" },
      { "sentenceId": "uuid", "text": "바람이 붑니다.", "recordingId": null, "recordingStatus": null }
    ],
    "createdAt": "2026-09-27T10:00:00"
  }
}
```

- `sentences`는 낭독 순서대로 온다.
- 아직 녹음하지 않은 문장은 `recordingId`, `recordingStatus`가 `null`이다.
- 같은 문장을 여러 번 녹음했으면 가장 최근 녹음이 온다.
- `status`: `IN_PROGRESS`(녹음 중) / `ANALYZED`(문장마다 가장 최근 녹음이 `DONE`이 되어 자모 오류 통계에 포함됨) / `COMPLETED`

에러: `RESOURCE_NOT_FOUND`(404), `FORBIDDEN_ACCESS`(403)

### 2.3 진단 녹음 업로드

`POST /diagnosis-sessions/{sessionId}/recordings` (multipart/form-data)

Request:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sentenceId` | string | 낭독한 문장 ID |
| `audioFile` | file (wav/m4a 등) | 음성 파일 |

Response (202 Accepted):

```json
{
  "success": true,
  "data": {
    "recordingId": "uuid",
    "sentenceId": "uuid",
    "status": "PROCESSING"
  }
}
```

> 업로드는 접수만 하고 바로 202를 반환한다. AI 인식은 비동기로 진행되므로 결과는 2.4를 폴링해서 확인한다.
녹음 상태는 `PROCESSING` → `DONE` 또는 `FAILED`로 바뀐다. `FAILED`면 같은 문장을 다시 녹음하면 된다.
>
- 분석이 끝난(`ANALYZED`) 세션에는 녹음을 추가할 수 없다.
- 파일 크기 상한은 10MB다.
- 업로드할 때 서버가 WAV(PCM16·모노·16kHz)로 변환한다. 브라우저 녹음(WebM/Opus, M4A/AAC, WAV)을 그대로 보내면 된다. 0.3초 미만·30초 초과·읽을 수 없는 파일은 접수하지 않고 바로 거절한다.

에러: `VALIDATION_FAILED`(400, 세션에 포함되지 않은 문장이거나 `sentenceId`·`audioFile`이 없음), `FORBIDDEN_ACCESS`(403, 본인 세션이 아님), `RESOURCE_NOT_FOUND`(404), `INVALID_STATE_TRANSITION`(409, 분석이 끝난 세션), `AUDIO_TOO_SHORT`·`AUDIO_TOO_LONG`·`AUDIO_INVALID`(400, 녹음 자체의 문제 — 0.6 참고), `AUDIO_PROCESSING_UNAVAILABLE`(503, 변환이 몰려 잠시 처리할 수 없음. 같은 녹음을 잠시 후 다시 보내면 된다).

### 2.4 녹음 인식 결과 조회

`GET /diagnosis-sessions/{sessionId}/recordings/{recordingId}/result`

Response (200) — 처리중:

```json
{
  "success": true,
  "data": {
    "status": "PROCESSING",
    "recognizedText": null,
    "answerText": "오늘 날씨가 좋습니다",
    "confidence": null,
    "diffHighlights": []
  }
}
```

Response (200) — 완료:

```json
{
  "success": true,
  "data": {
    "status": "DONE",
    "recognizedText": "오늘 씨가 조습니다",
    "answerText": "오늘 날씨가 좋습니다",
    "confidence": null,
    "diffHighlights": [
      { "position": 3, "expected": "날", "recognized": null },
      { "position": 7, "expected": "좋", "recognized": "조" }
    ]
  }
}
```

Response (200) — 실패:

```json
{
  "success": true,
  "data": {
    "status": "FAILED",
    "recognizedText": null,
    "answerText": "오늘 날씨가 좋습니다",
    "confidence": null,
    "diffHighlights": []
  }
}
```

`diffHighlights` 규칙:

- `position`은 `answerText` 안의 위치이며 **0부터, 공백을 포함해서** 센다. 프론트는 `answerText[position]`으로 바로 해당 글자를 찾을 수 있다.
- 글자가 바뀌었으면 `expected`와 `recognized`가 모두 있다.
- 정답의 글자가 인식에서 빠졌으면 `recognized`가 `null`이다.
- 정답에 없는 글자가 인식에 끼어들었으면 `expected`가 `null`이고, `position`은 끼어든 자리다.
- 띄어쓰기만 다른 것은 표시하지 않는다.
- 무음이라 `recognizedText`가 빈 문자열이면 `diffHighlights`는 빈 배열이다. 화면에서는 “음성이 인식되지 않았습니다”처럼 따로 안내한다.

`confidence`: 현재 AI 서버(v1)는 신뢰도를 제공하지 않아 `null`이다. 값이 오면 0~1 사이다.

에러: `RESOURCE_NOT_FOUND`(404), `FORBIDDEN_ACCESS`(403), `VALIDATION_FAILED`(400, 경로의 세션과 녹음의 실제 소속이 다름)

> `FAILED`는 AI 쪽 문제(서버 장애·시간 초과 등)로 인식하지 못한 경우다. 녹음 자체의 문제는 업로드 때 400으로 거절되므로 여기까지 오지 않는다. 같은 문장을 잠시 후 다시 녹음하면 된다. (2026-09-29에 계획했던 `failureReason`은 업로드 때 변환하면서 필요 없어져 만들지 않는다.)
>

### 2.5 자모 오류 통계 조회

`GET /users/me/jamo-error-stats`

> 사용자의 분석 완료(`ANALYZED`) 세션들을 **누적**해서 계산한다. 세션 하나(5문장)로는 자모별 표본이 모이지 않기 때문이다.
세션은 문장마다 가장 최근 녹음이 `DONE`이면 `ANALYZED`가 된다(2.2 조회에 보이는 녹음과 같다). 실패한 녹음이 남아 있어도 같은 문장을 다시 녹음해 `DONE`이면 된다. 다시 녹음한 것이 아직 인식 중이거나 실패했으면 예전 `DONE`이 있어도 끝나지 않는다. 통계에도 문장마다 가장 최근 녹음 하나만 쓰고, 그게 무음이면 그 문장은 뺀다.
표본이 부족해도 에러가 아니라 200으로 응답하고, 해당 자모는 `INSUFFICIENT_DATA`로 온다.
>
- **기준 표본 수(`minSupport`)는 10**이다(2026-09-29 결정). AI 기본값은 20이지만 세션 2~3개로는 거의 보이지 않아 진단 화면용으로 낮췄다. 시드 문장 기준으로 오류율을 보여줄 수 있는 자모 종류는 세션 1개면 약 2종류, 2개면 6종류, 3개면 10종류다(기준 20이면 각각 0, 2.4, 4종류).
- 진단 녹음은 개인화 여부와 무관하게 **항상 기본 모델**로 인식한다(2026-09-29 결정). 세션을 누적해도 서로 다른 모델의 결과가 한 통계에 섞이지 않게 하기 위해서다.
- 무음으로 인식된 녹음(빈 텍스트)은 통계에서 뺀다. 넣으면 정답의 모든 자모가 “빠졌다”로 세어진다.
- 새로 분석된 세션이 있으면 조회할 때 다시 계산하고, 없으면 저장된 결과를 돌려준다.

Response (200):

```json
{
  "success": true,
  "data": {
    "metricVersion": "jamo-err-v1",
    "minSupport": 10,
    "sessionsUsed": 3,
    "pairsUsed": 15,
    "tokens": [
      { "token": "ㅈ", "position": "INITIAL", "errors": 12, "sampleCount": 20, "errorRate": 0.6, "status": "OK" },
      { "token": "ㅆ", "position": "INITIAL", "errors": 3, "sampleCount": 7, "errorRate": null, "status": "INSUFFICIENT_DATA" }
    ]
  }
}
```

- `position`: `INITIAL`(초성) / `MEDIAL`(중성) / `FINAL`(종성). 같은 `ㄱ`이라도 초성과 종성은 따로 집계한다.
- `errorRate`: 표본(`sampleCount`)이 `minSupport`보다 적으면 `null`이다. 0.0은 “충분히 측정했고 한 번도 틀리지 않았다”는 뜻이라 서로 다르다.
- `sessionsUsed`, `pairsUsed`: 표본이 부족할 때 “세션을 더 진행해 주세요” 같은 안내에 쓴다.
- 분석한 세션이 하나도 없으면 `sessionsUsed: 0`, `tokens: []`로 온다(`metricVersion`은 `null`).
- 용어: 이 통계는 사용자의 발음이 아니라 **현재 인식 모델이 자주 틀리는 지점**을 나타낸다. 화면 문구도 “약한 발음”이 아니라 “모델이 자주 틀리는 지점”으로 쓴다.
- 표본이 적을 때는 “60%”보다 “10번 중 6번”처럼 횟수로 보여주는 것을 권한다(`errors`, `sampleCount`).

에러: `AI_INFERENCE_UNAVAILABLE`(503, 다시 계산해야 하는데 AI를 쓸 수 없을 때. 저장하지 않으므로 다음 조회에서 다시 시도된다)

---

## 3. 추천 문장 (담당: 백엔드 개발자 A)

### 3.1 개인화용 추천 문장 받기

`POST /users/me/recommendations` (인증 필요)

> 2026-09-29 계약 변경: 문장 선택은 AI(`/v1/enroll/next-prompts`, 7.6)가 하고, 백엔드는 **제안한 문장을 기록**해 전달한다(AI 계약 §1, §3.6). 호출할 때마다 새 문장이 나오고 기록이 생기므로 조회(GET)가 아니라 POST다. 세션과 무관하게 사용자 단위로 고른다.
>

Request (생략 가능):

```json
{ "count": 10 }
```

- `count`: 1~50, 생략하면 10

Response (200):

```json
{
  "success": true,
  "data": {
    "sentences": [
      { "shownPromptId": "uuid", "promptId": "02-03-0001", "text": "식당이 어디예요?" },
      { "shownPromptId": "uuid", "promptId": "06-01-0100", "text": "서울역으로 가주세요." }
    ]
  }
}
```

- `promptId`는 AI 문장 풀의 ID다. `shownPromptId`는 이 사용자에게 생성된 단일 추천 기록의 UUID다. 업로드(4.1)는 두 값을 함께 받아 중복 추천의 정확한 원문과 연결한다. 진단 `sentenceId`와 다른 ID 체계다.
- 이미 제안한 문장은 다음 추천에서 빠진다. 제안만 하고 읽지 않은 문장(예: 새로고침)도 빠지는데, 녹음하지 않은 문장을 다시 주는 “이어하기”는 개인화 녹음(4.1)이 생기면 정한다.
- 제안 기록은 화면 표시·녹음·학습 자격을 뜻하지 않는다.
- AI v1은 무작위(`random`) 선택만 지원한다. 오류 기반 선택(`error_based`)이 생기면 “이 문장이 노리는 자모” 같은 정보를 추가한다(기존 `targetPhonemes`는 그때까지 뺀다).

에러: `VALIDATION_FAILED`(400, `count`가 1~50을 벗어남), `AI_INFERENCE_UNAVAILABLE`(503, AI 서버 장애 또는 서버에 문장 풀 파일이 없음)

---

## 4. 개인화 (담당: 백엔드 개발자 B)

### 4.1 개인화용 추가 녹음 업로드

`POST /personalization/recordings` (multipart/form-data, 인증 필요)

| 파트 | 타입 | 설명 |
| --- | --- | --- |
| `metadata` | `application/json` | `shownPromptId`(추천 기록 UUID), `promptId`(문장 풀 ID), `storeAudio`(boolean, 생략 시 false), `useForTraining`(boolean, 생략 시 false) |
| `audioFile` | file | PCM WAV, WebM Opus/Vorbis, M4A AAC; 10 MB 이하, 0.3~30초 |

예: `metadata={"shownPromptId":"uuid","promptId":"02-03-0001","storeAudio":true,"useForTraining":false}`. 전달된 동의 값은 JSON boolean이어야 한다. null·문자열·숫자는 400이다. `storeAudio=false`이면 녹음을 저장하지 않고 400으로 거절한다. `useForTraining=false` 녹음도 저장할 수 있으나 학습 대상에서는 제외한다. 동의 정책 버전 `personalization-consent-v1`과 동의 시각을 함께 보존한다. 업로드 후 30일이 지나면 녹음과 파일을 삭제한다. 4.1.1절의 삭제 요청으로 더 일찍 철회할 수 있다. 요청의 원문은 무시하고 서버의 추천 기록 원문을 저장한다.

metadata는 4,096바이트 이하의 JSON 객체여야 하며, `shownPromptId`는 UUID 문자열, `promptId`는 비어 있지 않은 문자열이어야 한다. `application/json;charset=UTF-8`처럼 파라미터가 있는 JSON Content-Type도 허용한다. Content-Type 누락·잘못된 값, 빈 파트, 잘못된 JSON, 필드 타입 오류는 400 `VALIDATION_FAILED`이며 `error.message`로 원인을 안내한다. 저장 동의 누락과 문장 불일치도 각각 구체적인 메시지를 반환한다. 동의 값의 boolean 타입을 명확히 검증하기 위해 JSON 파트를 사용한다.

```javascript
const formData = new FormData();
formData.append('metadata', new Blob([JSON.stringify(meta)], { type: 'application/json' }));
formData.append('audioFile', audioFile);
// 요청 전체 Content-Type은 브라우저가 boundary와 함께 설정한다.
```

기본 보관 기간은 `voicebridge.personalization.retention-days=30`이며 `createdAt` 기준이다. 설정 변경은 기존 녹음에도 적용된다. 현재 학습 API가 503이어도 만료 정책은 적용된다. 팀·AI의 기간 합의는 별도 확인이 필요하다.

Response (201): `{"success":true,"data":{"recordingId":"uuid","status":"UPLOADED"}}`. 저장된 음성은 PCM16 모노 16kHz WAV다. `UPLOADED`는 정답 검토나 학습 가능 상태를 뜻하지 않는다.

에러: `VALIDATION_FAILED`(400, 누락·잘못된 metadata/동의/음성 또는 `promptId` 불일치), `FORBIDDEN_ACCESS`(403, 타인 추천 기록), `RESOURCE_NOT_FOUND`(404, 없는 추천 기록), `AUDIO_PROCESSING_UNAVAILABLE`(503, 변환 용량·시간 초과), `INTERNAL_SERVER_ERROR`(500, 변환 도구·DB·저장소 장애). 인증 실패는 0.6절 공통 응답을 따른다.

### 4.1.1 개인화 녹음 삭제·동의 철회

`DELETE /personalization/recordings/{recordingId}` (인증 필요)

Response (202, 본인 녹음): 본인 녹음을 즉시 학습 대상에서 제외하고 파일 삭제를 시도한다. 실패한 파일 삭제는 주기적 정리 작업에서 재시도한다. 응답 본문은 없다. 없는 녹음은 404 `RESOURCE_NOT_FOUND`, 타인 녹음은 403 `FORBIDDEN_ACCESS`다.

### 4.2 개인화 모델 학습 트리거

`POST /personalization/train` (인증 필요)

현재 AI v1의 train은 501이고 job 조회 경로가 없다. 서버는 job을 만들거나 202로 접수하지 않으며 503 `TRAINING_UNAVAILABLE`을 반환한다. 온라인 학습 계약과 적격 녹음의 정답 검토·동의·분할·전달 방식이 준비되면 202 계약을 별도 변경한다.

### 4.3 개인화 학습 상태 조회

`GET /personalization/train/{jobId}`

Response (200):

```json
{
  "success": true,
  "data": {
    "jobId": "uuid",
    "status": "IN_PROGRESS",
    "progress": null,
    "startedAt": "2026-09-14T09:00:00Z",
    "completedAt": null,
    "failureReason": null
  }
}
```

`status`: `PENDING` | `IN_PROGRESS` | `COMPLETED` | `FAILED`

`progress`는 AI가 검증 가능한 진행률을 제공하기 전까지 항상 `null`이다. `COMPLETED`는 학습 job 종료를 뜻하며 모델 활성화를 뜻하지 않는다. 없는 job은 404 `RESOURCE_NOT_FOUND`, 타인 job은 403 `FORBIDDEN_ACCESS`다.

### 4.4 내 개인화 모델 상태 조회

`GET /personalization/model`

Response (200):

```json
{
  "success": true,
  "data": {
    "hasPersonalizedModel": true,
    "modelVersion": "v3",
    "trainedAt": "2026-09-14T10:00:00Z",
    "trainingRecordingCount": 12
  }
}
```

`hasPersonalizedModel`은 실제 serving 확인을 마치고 `ACTIVE`로 기록된 adapter가 있을 때만 `true`다. `trainedAt`은 해당 adapter의 학습 완료 시각이다. 완료된 job이나 후보 adapter만 있으면 `false`이며 나머지 필드는 `null`이다.

---

## 5. 실사용 음성 인식 (담당: 백엔드 개발자 B)

### 5.1 음성 인식 요청

`POST /recognitions` (multipart/form-data, 인증 필요)

Request:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `audioFile` | file | 자유 발화 음성 |

처리 로직: serving 확인을 마친 활성 개인화 adapter가 있으면 우선 요청하고, 없으면 AI에 `use_adapter=false`를 보내 기본 모델을 강제한다. 활성화 확인 전의 완료 job은 개인화 모델로 표시하지 않는다. `modelUsed`는 AI 응답의 `model.adapter_id`가 null이면 `BASE_ADAPTED`, 값이 있으면 `PERSONALIZED`로 기록한다. 개인화를 요청했더라도 AI가 기본 모델을 반환하면 실제 사용 모델을 표시한다. 기본 모델을 강제했는데 adapter가 반환되면 계약 위반으로 503 `AI_INFERENCE_UNAVAILABLE`을 반환하고 인식 결과를 저장하지 않는다. `BASE_ADAPTED`는 기존 공개 값으로 남아 있으나 실제 의미는 기본 모델이며 명칭 정리는 별도 호환 변경으로 진행한다.

Response (200):

```json
{
  "success": true,
  "data": {
    "recognitionId": "uuid",
    "recognizedText": "병원에 가고 싶어요",
    "modelUsed": "PERSONALIZED",
    "confidence": 0.91
  }
}
```

`modelUsed`: `PERSONALIZED` | `BASE_ADAPTED`

에러:

- `VALIDATION_FAILED`(400): 손상·미지원 음성, 0.3초 미만·30초 초과 등 잘못된 업로드.
- `AUDIO_PROCESSING_UNAVAILABLE`(503): 음성 변환 슬롯 대기 초과 또는 변환 처리 시간 초과. 일시적 처리 불가이므로 재시도 가능.
- `AI_INFERENCE_UNAVAILABLE`(503): AI 추론 서버를 사용할 수 없음. 음성 변환 503과 `error.code`로 구분.
- `INTERNAL_SERVER_ERROR`(500): FFmpeg/ffprobe 미설치 등 변환 인프라 실패.

### 5.2 인식 이력 목록 조회

`GET /recognitions?page=0&size=20`

Response (200): 0.4절 공통 페이지네이션 포맷 + 각 항목은 5.1 응답과 동일 필드 + `createdAt`

### 5.3 인식 이력 상세 조회

`GET /recognitions/{recognitionId}`

Response (200): 5.1 응답 필드와 동일

---

## 6. TTS (2차 목표, 담당: 주영)

> 2026-09-26 설계 변경: AI팀 계약(§2/§5)의 “확인된 문장만 읽는다” 불변조건을 지키려면 자유 텍스트가 아니라 특정 인식 결과의 확인(confirmation) 레코드를 참조해야 한다. 그래서 실사용 인식(5장)에 confirm 단계를 추가하고, TTS 요청은 그 confirmationId를 참조하는 방식으로 바꿔다.
>

> ✅ 2026-09-26 엔진 결정: **네이버 클로바 보이스(NCP CLOVA Voice)**. 근거 — REST API로 기존 어댑터 패턴과 일관되고, 무료 할당량(월 100만자)이 데모 규모에 충분하며, 별도 GPU 인프라가 필요 없다. 음질 자체가 정량적으로 검증된 “최고”라서라기보다는 프로젝트 제약(인프라 부담, 일정) 대비 가장 실용적인 선택이라고 판단.
>

### 6.0 인식 결과 확인 (신규)

`POST /recognitions/{recognitionId}/confirm` (인증 필요)

Request:

```json
{ "confirmedText": "물 좀 주세요" }
```

Response (201):

```json
{
  "success": true,
  "data": {
    "confirmationId": "uuid",
    "confirmedText": "물 좀 주세요",
    "confirmedAt": "2026-09-26T09:00:00Z"
  }
}
```

같은 recognitionId로 새 confirmation이 생기면 이전 것은 자동으로 무효화된다(`valid=false`). `recognizedText`(AI 인식 결과)가 아니라 이 `confirmedText`(사용자가 최종 확정한 텍스트)만 TTS의 입력으로 신뢰한다 — 사용자가 인식 결과를 고쳤서 확인할 수 있기 때문이다.

에러: `RESOURCE_NOT_FOUND`(404, recognition 없음), `FORBIDDEN_ACCESS`(403, 타인 recognition), `VALIDATION_FAILED`(400, 빈 텍스트)

### 6.1 텍스트→음성 변환 요청

`POST /tts` (인증 필요)

Request:

```json
{ "confirmationId": "uuid", "idempotencyKey": "uuid" }
```

Response (202): `{ "ttsId": "uuid", "status": "PENDING" }`

대상 confirmation이 이미 무효화됐으면(`valid=false`) `INVALID_STATE_TRANSITION`(409)으로 거부한다 — “무효·미확인·취소된 문장은 절대 재생되지 않는다”는 불변조건을 지키는 지점이다. 같은 `idempotencyKey`로 재요청하면 같은 결과를 반환하고(중복 재생 방지), 다른 confirmationId에 이미 쓴 키를 재사용하면 `VALIDATION_FAILED`(400)로 거부한다.

### 6.2 TTS 결과 조회

`GET /tts/{ttsId}`

Response (200):

```json
{
  "success": true,
  "data": {
    "ttsId": "uuid",
    "status": "COMPLETED",
    "audioUrl": "https://s3.../tts/uuid.mp3"
  }
}
```

---

## 7. 내부 연동 API — Backend ↔︎ AI 추론 계층

> **v1은 HTTP(RestClient)로 확정되었다.** 실제 AI 서버 계약은 AI팀 `AI_BACKEND_CONTRACT_v1_EN.md`와 OpenAPI를 따른다. 아래 7.1·7.4는 초기 설계 시점의 추상 스펙으로 현재 구현 계약이 아니다. 7.2·7.3의 개인화 학습은 현재 지원 여부를 명시한다.
>

### 7.1 인식 요청

```
호출: recognize(audioSource: S3Url, modelType: BASE_ADAPTED | PERSONALIZED, userId: UUID?)
응답:
{
  "recognizedText": "string",
  "confidence": 0.0~1.0,
  "phonemeAlignments": [
    { "phoneme": "ㅈ", "startMs": 120, "endMs": 180, "confidence": 0.5 }
  ]
}
```

### 7.2 개인화 학습 트리거

현재 AI `POST /v1/adapters/train`은 501 `not_implemented_in_demo`만 정의·제공한다. BE는 AI 학습 요청을 보내거나 job을 만들지 않으며 공개 `POST /api/v1/personalization/train`에 503 `TRAINING_UNAVAILABLE`을 반환한다. 성공 요청·응답 형식과 녹음 전달 방식은 미확정이다(4.2절).

### 7.3 학습 상태 조회

AI `GET /v1/adapters/jobs/{job_id}` 경로는 현재 없다(404). BE 공개 `GET /api/v1/personalization/train/{jobId}`는 본인의 BE DB job만 읽는다. 응답의 `progress`는 실측값이 없어 `null`이며, 외부 worker의 진행이나 활성 adapter를 뜻하지 않는다(4.3절).

### 7.4 (2차 목표) TTS 변환

```
호출: synthesizeSpeech(text: string)
응답: { "audioS3Url": "string" }
```

### 7.5 자모 오류 통계 (실제 구현, AI 계약 §3.5)

```
POST /v1/analysis/jamo-errors
요청: { "pairs": [{ "ref": "정답 문장(등록 문장 원문)", "hyp": "인식 결과" }], "min_support": 10 }
응답: metric_version, min_support, pairs_used,
      tokens[{ token, position(initial|medial|final), errors, sample_count, error_rate(null 가능), status(ok|insufficient_data) }]
```

- `ref`에는 모델 출력이 아니라 **문장 원문**만 넣는다. 빈 `pairs`는 AI가 422로 거절하므로 보내지 않는다.
- 구현: `HttpJamoStatsClient` (로컬 프로파일은 `StubJamoStatsClient`)

### 7.6 추천 문장 (실제 구현, AI 계약 §3.6)

```
POST /v1/enroll/next-prompts
요청: { "user_id": "uuid", "n": 10, "strategy": "random", "seed": 호출마다 새 값, "exclude_prompt_ids": [이미 제안한 ID] }
응답: strategy, strategy_version, seed, pool_size, prompts[{ prompt_id, text }]
```

- `seed`가 같으면 같은 문장이 나오므로 호출마다 새로 뽑고 기록한다. `coverage`·`error_based` 전략은 501.
- 서버에 문장 풀 파일(`data/script_pool.json`)이 없으면 503(`prompt_pool_missing`).
- 구현: `HttpEnrollmentPromptClient` (로컬 프로파일은 `StubEnrollmentPromptClient`)

> 7.5·7.6 공통: AI 데모 서버는 `Content-Length`로만 요청 본문을 읽는다. JSON을 조각(chunked)으로 보내면 빈 요청으로 처리되므로(422) 본문을 모아서 보낸다.
>

---

## 8. 엔드포인트 요약표

| Method | Path | 담당 | 인증 |
| --- | --- | --- | --- |
| POST | /auth/signup | BE-A | X |
| POST | /auth/login | BE-A | X |
| POST | /auth/refresh | BE-A | X |
| GET | /users/me | BE-A | O |
| POST | /diagnosis-sessions | BE-A | O |
| GET | /diagnosis-sessions/{id} | BE-A | O |
| POST | /diagnosis-sessions/{id}/recordings | BE-A | O |
| GET | /diagnosis-sessions/{id}/recordings/{rid}/result | BE-A | O |
| GET | /users/me/jamo-error-stats | BE-A | O |
| POST | /users/me/recommendations | BE-A | O |
| POST | /personalization/recordings | BE-B | O |
| DELETE | /personalization/recordings/{recordingId} | BE-B | O |
| POST | /personalization/train | BE-B | O |
| GET | /personalization/train/{jobId} | BE-B | O |
| GET | /personalization/model | BE-B | O |
| POST | /recognitions | BE-B | O |
| GET | /recognitions | BE-B | O |
| GET | /recognitions/{id} | BE-B | O |
| POST | /recognitions/{id}/confirm | 주영 | O |
| POST | /tts | 주영(stretch) | O |
| GET | /tts/{id} | 주영(stretch) | O |

---

## 9. 변경 이력

| 날짜 | 절 | 변경 |
| --- | --- | --- |
| 2026-09-27 | 2.2 | 응답 예시 추가 (문장별 녹음 상태) |
| 2026-09-27 | 2.3 | `201`→`202`, `UPLOADED`→`PROCESSING`, `FAILED` 흐름 |
| 2026-09-27 | 2.4 | `diffHighlights` 규칙, 처리중·실패 예시, `confidence` null |
| 2026-09-27 | 2.5 | 취약 음소 분석 → 자모 오류 통계 (`/users/me/jamo-error-stats`, 세션 누적) |
| 2026-09-29 | 0.6, 2.3 | 필수 값 누락은 400(`VALIDATION_FAILED`), 업로드 에러 목록 복구·보강 (PR #35) |
| 2026-09-29 | 2.3, 2.5 | 분석이 끝난 세션 업로드는 409, `minSupport` 10, 진단은 항상 기본 모델, 무음 녹음 제외 |
| 2026-09-29 | 2.4 | 실패 사유(`failureReason`) 추가 예정 |
| 2026-09-29 | 3.1 | 추천 문장 `GET /recommendations` → `POST /users/me/recommendations`, `promptId`, `sessionId`·`targetPhonemes` 삭제 (PR #37) |
| 2026-09-30 | 7.5, 7.6 | 자모 오류 통계·추천 문장의 실제 AI 호출 추가 |
| 2026-09-30 | 0.5, 0.6, 5.1 | 오디오 변환 용량·시간 초과 시 `AUDIO_PROCESSING_UNAVAILABLE`(503) 추가 (PR #39). 진단 업로드 변환에도 동일 코드 사용 |
| 2026-09-30 | 2.2, 2.5 | 세션 완료와 통계 모두 문장마다 가장 최근 녹음 기준 (PR #38 리뷰 반영) |
| 2026-09-30 | 2.3 | 지원하지 않는 오디오 형식: 422(예정) → 400 VALIDATION_FAILED (실사용 인식 PR #39와 맞춤) |
| 2026-10-02 | 0.6, 3.1, 4.1~4.2 | 추천 기록 ID, 개인화 WAV 업로드·삭제·30일 보관 계약 추가; 학습 API는 외부 계약 미지원으로 503 반환 |
| 2026-10-02 | 4.3~4.4, 5.1 | 진행률 미지원 시 `progress: null` 명시; 완료 job과 활성 adapter를 분리해 모델 보유·인식 표시 기준 수정 |
| 2026-10-02 | 7.2~7.3 | 초기 학습 성공 예시를 현재 AI 501/job 경로 부재 및 BE 503·DB 조회 계약으로 정정 |
| 2026-10-02 | 5.1 | 기본 모델 요청 시 `use_adapter=false` 전달, AI 응답의 adapter ID로 `modelUsed` 판정 및 요청·응답 불일치 503 처리 명시 |
| 2026-10-03 | 4.1 | JSON Content-Type 파라미터 허용, metadata 크기·문자열 타입 검증 및 원인별 오류 메시지·Blob 예시 추가; 보관 설정 기준 명시 (PR #40 리뷰 반영) |
| 2026-10-03 | 0.6, 2.3, 2.4 | 진단 업로드 시 WAV 변환. 녹음 문제는 `AUDIO_TOO_SHORT`·`AUDIO_TOO_LONG`·`AUDIO_INVALID`(400), 변환 지연은 `AUDIO_PROCESSING_UNAVAILABLE`(503). `FAILED`는 AI 쪽 문제뿐이라 `failureReason` 계획 취소 (PR #43) |
