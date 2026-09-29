# 진단 세션(diagnosis-session) 다음 구현 가이드 — 백엔드 A

5개 엔드포인트 중 **4개 구현 완료**. 남은 것은 취약 음소 분석 하나다.

## 구현 현황

| 엔드포인트 | 상태 |
|---|---|
| POST /diagnosis-sessions | 완료 (PR #7) |
| GET /diagnosis-sessions/{id} | 완료 |
| POST /diagnosis-sessions/{id}/recordings | 완료 (PR #24) |
| GET .../recordings/{rid}/result | 완료 |
| GET .../weak-phonemes | **미착수** |

## 이미 갖춰진 기반

- `domain/diagnosis/Recording` — 팩토리 `create()`/`reconstitute()`, 상태 전이
  `markProcessing()`/`markProcessed()`/`markFailed()`, 질의 `isOwnedBy()`/`isDone()`
- `domain/diagnosis/RecordingStatus` — `UPLOADED` / `PROCESSING` / `DONE` / `FAILED`
- `port/out/RecordingRepositoryPort` — `save` / `findById` / `findBySessionId`
- `port/out/StoragePort` — S3 어댑터와 로컬 파일 어댑터를 프로파일로 분리.
  키 생성 규칙은 `StorageKeys` 공통. AWS 크레덴셜 없이 개발 가능
- `port/out/SentenceRepositoryPort.findAllByIds` — 문장 ID로 원문 조회
- 비동기 인식: `RecordingUploadedEvent` + `RecordingRecognitionHandler`
  (`@Async` + `@TransactionalEventListener(AFTER_COMMIT)`)

### 건드리기 전에 알아야 할 규칙

- `markProcessed()`는 **빈 문자열을 정상 결과로 받아들인다.** 무음·비언어 오디오에서
  JPyRust가 `{"recognized_text": "", "confidence": 0.0}`를 정상 응답으로 돌려주기 때문.
  여기를 막으면 해당 녹음이 `PROCESSING`에 갇혀 세션 전체가 완료 불가가 된다.
  기본(v1) 구현체인 HTTP 어댑터(`HttpAiInferenceClient`)에도 같은 계약이 그대로 적용된다 —
  `status: no_speech`(빈 텍스트)도 정상 응답이고, `confidence`는 이제 `Double`(nullable)이며
  AI 서버 v1 계약상 `score`는 항상 `null`이다. `markProcessed()`는 `confidence == null`이면
  범위 검증을 생략하고 그대로 반영한다.
- `Recording.userId`는 `DiagnosisSession`과 중복되는 **의도된 비정규화**다. 이 값을 신뢰하려면
  `create()` 호출 전에 반드시 `DiagnosisSession.isOwnedBy()`로 세션 소유권을 먼저 검증해야 한다.
- `@Async` 메서드를 같은 클래스 안에서 호출하면 프록시를 거치지 않아 **조용히 동기로
  실행된다**(에러도 안 남). 반드시 별도 빈으로 분리할 것.

---

## 취약 음소 분석 — 착수 전 메모

세부 설계(인터페이스 시그니처, 응답 형태, AI 호출 방식)는 **팀 논의 진행 중**이다.
결론이 나면 이 문서에 반영한다. 아래는 그 논의의 입력으로 확보해 둔 사실만 적는다.

### 통계는 여러 세션을 누적해 계산한다 (2026-09-24 결정)

세션당 5문장으로는 자모별 표본이 모이지 않는다. 문장 DB에서 5문장을 무작위로 뽑아
30회 평균한 실측:

| 세션 수 | 누적 자모 수 | 표본 20개 이상 모인 자모 종류 |
|---|---|---|
| 1 | 97개 | **0종류** |
| 2 | 196개 | 2.5종류 |
| 3 | 291개 | 4종류 |
| 5 | 490개 | 8.5종류 |

한 세션의 문장 수를 늘리는 대신 **세션을 누적**하는 방향으로 정했다. 진단 부담을
키우지 않으면서 표본을 확보할 수 있기 때문. 참고로 선행연구
(Awasthi et al., ICASSP 2021)의 시드셋은 50문장이다.

이 결정에 따라 `markAnalyzed()`는 **코드를 바꾸지 않고 의미만 재정의**하면 된다 —
"이 세션의 녹음이 전부 끝나 통계 집계 대상이 되었다". 누적 계산은 `ANALYZED` 세션들만
모으면 되고, 계산 자체는 세션 상태와 무관하게 언제든 다시 할 수 있다.

### 자모 분해는 백엔드가 구현하지 않는다

정답 문장과 인식 결과 쌍만 AI에 넘기면 자모 단위 오류 집계는 AI가 돌려준다.
단, 정답으로 넘기는 텍스트는 **등록 프롬프트나 사람이 교정한 텍스트**여야 하고
모델 출력을 정답으로 되먹이면 안 된다.

## 추천 문장 (API 명세서 3장) — 구현, PR 대기

진단 세션 5개와 함께 백엔드 A 담당이다. 9/26 전수조사에서 "어느 문서에도 추적되지 않던 항목"으로 지적돼 여기서 함께 관리한다.

- 문장 선택은 AI가 하고 백엔드는 보여준 문장을 기록한다. 명세서 3.1과 계약이 네 군데 달라졌다(PROGRESS 22번 참고).
- **실제 연결 전 필요**: AI 서버에 문장 풀 파일(`data/script_pool.json`)이 있어야 한다. 없으면 항상 503.
- **개인화 녹음(FR-7, 백엔드 B)과의 연결점**: 녹음이 어떤 문장을 읽었는지는 `promptId`로 연결한다. 그 `promptId`가 이 사용자에게 실제로 보여준 문장인지, 원문이 무엇이었는지는 `shown_prompts`가 근거다. 검증이 필요해지면 `ShownPromptRepositoryPort`에 조회 메서드를 추가한다.

## 그 외 주의

- 새로 만드는 영속성 어댑터는 `UserPersistenceAdapter.save()`처럼 **JPA 예외를 도메인
  예외로 번역**할 것.
- 도메인이 던지는 `IllegalStateException`/`IllegalArgumentException`은 PR #27의 전역
  핸들러가 각각 409/400으로 매핑한다. 도메인이 `CustomException`/`ErrorCode`를 직접
  참조하게 하지 말 것(도메인 순수성).
- 테스트는 도메인 로직(단위, Mockito 없이)과 application 서비스(Mockito 목) 두 종류로 —
  `RecordingTest`, `UploadDiagnosisRecordingServiceTest` 패턴 참고.
- 6장 코드 컨벤션(Spotless, record 네이밍, MockMvc/SpringBootTest 구분, `@MockitoBean`) 적용.
