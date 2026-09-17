# 개인화·인식(personalization/recognition) 다음 구현 가이드 — 백엔드 B

이번에 받은 건 "계약(7개 API) + 모델 상태 조회 1개 완전 구현"까지야.

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
| GET /personalization/train/{jobId} | `GetPersonalizationTrainingStatusUseCase` | 단순 조회 — GetPersonalizationModelService 패턴 그대로 참고 |
| POST /recognitions | `RecognizeSpeechUseCase` | 개인화 모델 있으면 우선 사용, 없으면 기본 모델 폴백. findLatestCompletedByUserId()로 확인 |
| GET /recognitions | `GetRecognitionHistoryUseCase` | 페이지네이션. `Recognition` 리포지토리 포트/어댑터 아직 없음 — 새로 정의할 것 |
| GET /recognitions/{id} | `GetRecognitionDetailUseCase` | 본인 소유 체크는 `Recognition.isOwnedBy()` |

## 구현 순서 추천

1. `TrainPersonalizationModelUseCase` — `PersonalizationJobRepositoryPort`가 이미 완성돼 있어서 바로 시작 가능
2. `UploadPersonalizationRecordingUseCase` — S3 업로드 필요. 민수가 diagnosis-session에서
   StoragePort를 먼저 만들면 그거 재사용, 아직 없으면 서로 맞춰서 같이 설계할 것
3. `GetPersonalizationTrainingStatusUseCase` — 단순 조회
4. `RecognizeSpeechUseCase` + 이력 조회 2개 — `Recognition` 리포지토리 포트/어댑터부터 새로 설계
   (`DiagnosisSessionPersistenceAdapter` 스타일 그대로 따라 하면 됨)

## 주의

- `AiInferenceClient` 구현체는 아직 없음(JPyRust vs FastAPI PoC 대기중) — 실제 인식 로직은
  이 포트가 스텁으로라도 구현되기 전까진 완성 못 함. 주영한테 PoC 진행 상황 확인하고 진행할 것.
- `PersonalizationController`에 새 엔드포인트 추가할 때마다 상단 TODO 주석에서 하나씩 지워나갈 것.
- 6장 코드 컨벤션(Spotless, record 네이밍, MockMvc/SpringBootTest 구분, `@MockitoBean`) 그대로 적용.
