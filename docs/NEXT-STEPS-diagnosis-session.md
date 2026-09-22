# 진단 세션(diagnosis-session) 다음 구현 가이드 — 백엔드 A

계약(5개 API)과 "세션 시작" 1개 완전 구현이 PR #7로 들어와 있고, 그 위에
`Recording` 도메인(PR #21)까지 정의된 상태다. 남은 4개는 아래 순서로 채우면 된다.
패턴은 `AuthController` / `LoginService` / `UserPersistenceAdapter`를 그대로 따라 하면 됨.

## 남은 엔드포인트 (API 명세서 2장)

| 엔드포인트 | port.in 인터페이스 | 비고 |
|---|---|---|
| GET /diagnosis-sessions/{id} | `GetDiagnosisSessionUseCase` | 본인 소유 아니면 FORBIDDEN_ACCESS — `DiagnosisSession.isOwnedBy()` 사용 |
| POST /diagnosis-sessions/{id}/recordings | `UploadDiagnosisRecordingUseCase` | multipart 업로드. S3 저장 포트가 아직 없음 — 아래 "선행 작업" 참고 |
| GET .../recordings/{rid}/result | `GetRecordingResultUseCase` | AI 인식 결과 조회. 폴링 방식이라 `PROCESSING` 상태를 그대로 응답 |
| GET .../weak-phonemes | `AnalyzeWeakPhonemesUseCase` | 세션의 모든 녹음이 DONE이어야 호출 가능. `DiagnosisSession.markAnalyzed()`가 이 상태 전이를 담당 |

## 이미 준비된 것 (PR #21)

- `domain/diagnosis/Recording` — 팩토리 `create()`/`reconstitute()`, 상태 전이 `markProcessing()`/`markProcessed()`, 질의 `isOwnedBy()`/`isDone()`
- `domain/diagnosis/RecordingStatus` — `UPLOADED` / `PROCESSING` / `DONE`
- `port/out/RecordingRepositoryPort` — `save` / `findById` / `findBySessionId`

주의할 규칙 두 가지:

- `markProcessed()`는 **빈 문자열을 정상 결과로 받아들인다.** 무음·비언어 오디오에서
  JPyRust가 `{"recognized_text": "", "confidence": 0.0}`를 정상 응답으로 돌려주기 때문.
  여기를 막으면 해당 녹음이 `PROCESSING`에 갇혀 세션 전체가 완료 불가가 된다.
- `Recording.userId`는 `DiagnosisSession`과 중복되는 **의도된 비정규화**다. 이 값을 신뢰하려면
  `create()` 호출 전에 반드시 `DiagnosisSession.isOwnedBy()`로 세션 소유권을 먼저 검증해야 한다.

## 선행 작업 — 녹음 업로드 착수 전에 필요한 것

- `build.gradle`에 AWS SDK 추가 (`software.amazon.awssdk:s3`) — 현재 없음
- `port/out/StoragePort` 정의:
  ```java
  public interface StoragePort {
      String upload(byte[] fileBytes, String fileName);  // 반환값: 저장된 경로
  }
  ```
- `adapter/out/storage`에 구현체 — 현재 `package-info.java`만 있는 빈 패키지
- 로컬 개발에서 S3를 무엇으로 대체할지 결정 (실제 버킷 / LocalStack / 파일시스템 어댑터를
  프로파일로 분리) — 인프라 담당 확인 필요

## 구현 순서 추천

1. `UploadDiagnosisRecordingUseCase` — 위 선행 작업부터. 흐름은
   컨트롤러가 multipart 수신 → `StoragePort`로 S3 저장 → `Recording.create()`로 `UPLOADED` 저장
   → AI 인식은 **비동기로 트리거**(사용자가 인식 시간까지 기다리지 않게)
2. `GetRecordingResultUseCase`, `GetDiagnosisSessionUseCase` — 단순 조회라
   `GetMyProfileService` 패턴 그대로
3. `AnalyzeWeakPhonemesUseCase` — 제일 복잡하니 마지막에. 음소 분석 알고리즘 자체는 AI팀 담당이라
   여기선 AI가 준 데이터를 집계해 API 응답으로 만드는 부분만. **착수 전 AI 담당과 주고받을
   데이터 형식부터 합의할 것** (`AiInferenceClient.RecognitionResult`의 `phonemeAlignments`가 아직 TODO)

## 주의

- AI 인식 **비동기 방식은 미결정**이다(동기 / `@Async` / 스프링 이벤트 + `@TransactionalEventListener`).
  판단은 담당자에게 위임됐고 코드리뷰에서 함께 논의한다. 비동기를 택하면 서버 재시작으로
  `PROCESSING`에 갇힌 녹음을 되살릴 복구 수단을 함께 설계할지도 정해야 한다.
- `@Async`를 쓸 경우 **같은 클래스 안에서 호출하면 동작하지 않는다**(AOP self-invocation).
  반드시 별도 빈으로 분리할 것 — CLAUDE.md 3장에 명시된 안티패턴.
- 새로 만드는 영속성 어댑터는 `UserPersistenceAdapter.save()`처럼 **JPA 예외를 도메인 예외로 번역**할 것.
- `DiagnosisSessionController` 상단 TODO 주석 — 엔드포인트 하나 구현할 때마다 거기서 하나씩 지워나갈 것.
- 6장 코드 컨벤션(Spotless, record 네이밍, MockMvc/SpringBootTest 구분, `@MockitoBean`) 그대로 적용.
- 테스트는 도메인 로직(단위, Mockito 없이)과 application 서비스(Mockito 목) 두 종류로 —
  `RecordingTest`, `StartDiagnosisSessionServiceTest` 패턴 참고.
