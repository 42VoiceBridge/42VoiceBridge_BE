# 진단 세션(diagnosis-session) 다음 구현 가이드 — 백엔드 A

이번에 받은 건 "계약(5개 API) + 세션 시작 1개 완전 구현"까지야.
나머지 4개는 아래 순서로 채우면 돼. 패턴은 `feature/auth`의
`AuthController` / `LoginService` / `UserPersistenceAdapter`를 그대로 따라 하면 됨.

## 남은 엔드포인트 (API 명세서 2장)

| 엔드포인트 | port.in 인터페이스 | 비고 |
|---|---|---|
| GET /diagnosis-sessions/{id} | `GetDiagnosisSessionUseCase` | 본인 소유 아니면 FORBIDDEN_ACCESS — `DiagnosisSession.isOwnedBy()` 사용 |
| POST /diagnosis-sessions/{id}/recordings | `UploadDiagnosisRecordingUseCase` | multipart 업로드. `Recording` 도메인 클래스가 아직 없음 — User/DiagnosisSession 스타일로 새로 설계할 것 |
| GET .../recordings/{rid}/result | `GetRecordingResultUseCase` | AI 인식 결과 조회. `port.out.AiInferenceClient`(스텁) 참고 |
| GET .../weak-phonemes | `AnalyzeWeakPhonemesUseCase` | 세션의 모든 녹음이 DONE이어야 호출 가능. `DiagnosisSession.markAnalyzed()`가 이 상태 전이를 담당 |

## 구현 순서 추천

1. `Recording` 도메인 클래스부터 만들기 (팩토리 메서드, 상태 UPLOADED/PROCESSING/DONE,
   `markProcessed(text, confidence)` 같은 Rich Domain 메서드로 — `DiagnosisSession.java` 참고)
2. `UploadDiagnosisRecordingUseCase` 구현 — S3 업로드는 `adapter.out.storage`에 새 어댑터 필요
   (아직 없음, `port.out`에 저장소 포트부터 정의할 것)
3. `GetRecordingResultUseCase`, `GetDiagnosisSessionUseCase` — 단순 조회라
   `feature/auth`의 `GetMyProfileService` 패턴 그대로 따라 하면 됨
4. `AnalyzeWeakPhonemesUseCase` — 제일 복잡하니 마지막에

## 주의

- `AiInferenceClient`(port.out)는 계약만 잡혀 있고 구현체는 없음(JPyRust PoC 대기, AI 연동 담당 B 영역).
  스텁이 필요하면 먼저 B랑 맞춰서 진행할 것.
- `DiagnosisSessionController` 상단 TODO 주석 — 엔드포인트 하나 구현할 때마다 거기서 하나씩 지워나갈 것.
- 6장 코드 컨벤션(Spotless, record 네이밍, MockMvc/SpringBootTest 구분, `@MockitoBean`) 그대로 적용.
