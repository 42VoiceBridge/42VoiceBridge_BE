# 개인화·인식(personalization/recognition) 다음 구현 가이드 — 백엔드 B

현재는 계약(7개 API)과 모델 상태 조회·학습 작업 상태 조회 2개가 구현되어 있어.

## 학습 작업 상태 조회 — 구현 완료 (2026-09-18)

- `GET /api/v1/personalization/train/{jobId}`: 로그인 사용자의 작업 상태, 시작 시각, 완료 시각, 실패 사유를 반환.
- 없는 작업은 `RESOURCE_NOT_FOUND`(404), 다른 사용자의 작업은 `FORBIDDEN_ACCESS`(403), UUID 변환 실패는 `VALIDATION_FAILED`(400).
- `GetPersonalizationTrainingStatusService` → 기존 `PersonalizationJobRepositoryPort.findById()`로 조회.
- 실제 AI 호출 없이 DB에 저장된 상태를 읽는다. 학습 상태 갱신 및 `progress` 전달은 별도 구현 대상.
- H2와 MockMvc를 사용하는 `PersonalizationTrainingStatusIntegrationTest`에서 네 가지 작업 상태와 예외·접근 제어를 검증.

## ⚠️ 먼저 확인할 것

`port.out.AiInferenceClient`가 이번에 살짝 바뀌었어 — 원래 그 안에 직접 정의돼 있던
`ModelType` enum을 `domain.recognition.ModelType`으로 옮겼어 (SSOT: Recognition 도메인도
동일한 값을 쓰는데 두 군데서 따로 정의하면 나중에 어긋날 수 있어서). AiInferenceClient를
참고해서 이미 뭔가 짜고 있었다면 import만 바꾸면 됨.

## 남은 엔드포인트 (API 명세서 4~5장)

| 엔드포인트 | port.in 인터페이스 | 비고 |
|---|---|---|
| POST /personalization/recordings | `UploadPersonalizationRecordingUseCase` | feature/diagnosis-session의 업로드 패턴 참고 |
| POST /personalization/train | `TrainPersonalizationModelUseCase` | 최소 녹음 수 미달 시 INSUFFICIENT_RECORDINGS, 중복 실행 시 INVALID_STATE_TRANSITION — findInProgressByUserId() 사용 |
| POST /recognitions | `RecognizeSpeechUseCase` | 개인화 모델 있으면 우선 사용, 없으면 기본 모델 폴백. findLatestCompletedByUserId()로 확인 |
| GET /recognitions | `GetRecognitionHistoryUseCase` | 페이지네이션. `Recognition` 리포지토리 포트/어댑터 아직 없음 — 새로 정의할 것 |
| GET /recognitions/{id} | `GetRecognitionDetailUseCase` | 본인 소유 체크는 `Recognition.isOwnedBy()` |

## Confirmation/TTS 게이트 — 신규 도메인, 구현 완료 (PR #31, 2026-09-26)

위 6개 유스케이스 목록에는 원래 없던 신규 도메인이지만, 인식 결과(Recognition)를 다루는 같은
`RecognitionController`에 엔드포인트가 추가돼 있어 여기 같이 기록한다.

- `POST /api/v1/recognitions/{recognitionId}/confirm`: 인식 결과 확인. 같은 recognitionId로
  재확인하면 이전 확인은 무효화된다.
- `POST /api/v1/tts`: 확인된 텍스트로 TTS 요청. 무효화된 confirmation으로는 요청 불가.
- `GET /api/v1/tts/{ttsId}`: TTS 요청 상태 조회.
- `TtsEnginePort` 구현체(`NaverClovaVoiceAdapter`, CLOVA Voice TTS Premium)와 비동기 합성 흐름까지
  구현 완료 — `RequestTtsService`가 `TtsRequestedEvent`를 발행하면 `TtsSynthesisHandler`가
  `@Async` + `AFTER_COMMIT`으로 합성 후 `StoragePort`에 저장, `COMPLETED`/`FAILED`로 전이한다.

## 구현 순서 추천

1. `TrainPersonalizationModelUseCase` — `PersonalizationJobRepositoryPort`가 이미 완성돼 있어서 바로 시작 가능
2. `UploadPersonalizationRecordingUseCase` — S3 업로드 필요. 민수가 diagnosis-session에서
   StoragePort를 먼저 만들면 그거 재사용, 아직 없으면 서로 맞춰서 같이 설계할 것
3. `RecognizeSpeechUseCase` + 이력 조회 2개 — `Recognition` 리포지토리 포트/어댑터부터 새로 설계
   (`DiagnosisSessionPersistenceAdapter` 스타일 그대로 따라 하면 됨)

## 주의

- `AiInferenceClient` 구현체는 이미 있음 — HTTP(`RestClient`) 기반 `adapter/out/ai/HttpAiInferenceClient.java`가
  기본(v1) 구현체다(PR #29). JPyRust 구현체(`JPyRustAiInferenceClient`)는 삭제되지 않고
  `jpyrust-experiment` 프로파일로 실험적으로 보존 중이며 기본 프로파일에서는 비활성화되어 있다.
- `PersonalizationController`에 새 엔드포인트 추가할 때마다 상단 TODO 주석에서 하나씩 지워나갈 것.
- 6장 코드 컨벤션(Spotless, record 네이밍, MockMvc/SpringBootTest 구분, `@MockitoBean`) 그대로 적용.
