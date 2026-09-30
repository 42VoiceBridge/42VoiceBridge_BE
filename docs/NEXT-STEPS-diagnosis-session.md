# 진단 세션(diagnosis-session) 다음 구현 가이드 — 백엔드 A

5개 엔드포인트 **모두 구현 완료**. 취약 음소 분석은 계약 변경을 거쳐 **자모 오류 통계**가 됐다.

## 구현 현황

| 엔드포인트 | 상태 |
|---|---|
| POST /diagnosis-sessions | 완료 (PR #7) |
| GET /diagnosis-sessions/{id} | 완료 |
| POST /diagnosis-sessions/{id}/recordings | 완료 (PR #24) |
| GET .../recordings/{rid}/result | 완료 |
| ~~GET .../weak-phonemes~~ → `GET /users/me/jamo-error-stats` | 완료 (PR #38 리뷰 중) — 세션 단위에서 사용자 단위 누적으로 계약 변경 |

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

## 자모 오류 통계 (구 취약 음소 분석) — 구현

설계 결정과 이유는 PROGRESS 23번에 있다. 여기에는 **이 코드를 건드릴 사람이 알아야 할 것**만 적는다.

- 세션은 "문장마다 **가장 최근 녹음**이 `DONE`"이면 `ANALYZED`가 된다. 판단은 인식 트랜잭션이 **커밋된 뒤** 별도 빈(`DiagnosisSessionAnalysisTrigger`)에서 한다. 이걸 인식 핸들러 안으로 옮기면 동시에 끝난 녹음끼리 서로의 결과를 못 봐서 세션이 영원히 끝나지 않는다.
- "문장마다 가장 최근 녹음"은 `Recording.latestPerSentence` 하나로 정한다. 세션 완료 판단, 자모 통계의 쌍, 세션 조회가 같이 쓴다. 한 곳만 규칙을 바꾸면 분석이 끝난 세션의 통계 재료가 나중에 바뀔 수 있다.
- 녹음 등록(`DiagnosisRecordingRegistrar.register`)과 완료 판단은 **세션 행을 잠근다**(`findByIdForUpdate`). 한쪽에서 잠금을 빼면 분석이 끝난 세션에 녹음이 들어간다(`DiagnosisRecordingRaceTest`가 막음). S3 업로드는 잠금 밖에 둘 것 — 업로드 서비스에 `@Transactional`을 다시 걸면 S3를 기다리는 동안 세션이 잠긴다.
- 잠금·동시성 테스트는 H2가 아니라 MySQL 컨테이너(`support/MySqlContainerTest`)로 돌린다. H2는 잠금 경합에서 MySQL과 다르게 실패한다.
- 통계는 조회 시점에 스냅샷이 낡았는지(`isStale`) 보고 필요하면 다시 계산한다. 서비스에 `@Transactional`을 걸지 말 것 — AI 응답을 기다리는 동안 DB 연결을 붙잡게 된다.
- 쌍은 **(세션, 문장)** 기준으로 묶는다. 문장 ID로만 묶으면 누적해도 표본이 늘지 않는다.
- 무음 녹음은 뺀다(9/29 결정). 정답 텍스트는 반드시 문장 원문.
- AI 호출 어댑터는 요청 본문을 버퍼링해야 한다(데모 서버가 chunked 본문을 읽지 못함).
- **실제 AI 연결 전 필요**: 진단 녹음을 WAV로 변환하지 않으면 전부 `FAILED`가 되어 통계가 비어 있다(백엔드 B P02 머지 후 연결).
- 진단 인식은 **항상 기본 모델**(`RecordingRecognitionHandler.DIAGNOSIS_MODEL`, 9/29 결정). 개인화 모델로 바꾸지 말 것 — 누적 통계에 서로 다른 모델의 결과가 섞인다.
- 진단 인식은 **전용 실행기**(`AsyncConfig.DIAGNOSIS_RECOGNITION_EXECUTOR`, 스레드 2·대기열 200)에서 돈다. 설정의 `spring.task.execution.mode: force`를 지우면 TTS 같은 다른 `@Async`까지 이 실행기로 몰린다(`AsyncExecutorRoutingTest`가 막음).
- `min_support`는 10(설정값 `voicebridge.ai.jamo-stats.min-support`). AI 담당에게는 통계적으로 괜찮은지만 확인 요청.
- 실패 사유 4가지(`AUDIO_TOO_SHORT`·`AUDIO_TOO_LONG`·`AUDIO_INVALID`·`AI_UNAVAILABLE`)는 P02 연결 PR에서 구현한다.

### 근거: 통계는 여러 세션을 누적해 계산한다 (2026-09-24 결정)

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

## 추천 문장 (API 명세서 3장) — 구현 완료 (PR #37)

진단 세션 5개와 함께 백엔드 A 담당이다. 9/26 전수조사에서 "어느 문서에도 추적되지 않던 항목"으로 지적돼 여기서 함께 관리한다.

- 문장 선택은 AI가 하고 백엔드는 제안한 문장을 기록한다. 명세서 3.1과 계약이 네 군데 달라졌다(PROGRESS 22번 참고).
- **실제 연결 전 필요**: AI 서버에 문장 풀 파일(`data/script_pool.json`)이 있어야 한다. 없으면 항상 503.
- **개인화 녹음(FR-7, 백엔드 B)과의 연결점**: 녹음이 어떤 문장을 읽었는지는 `promptId`로 연결한다. 그 문장이 이 사용자에게 제안된 것인지와 원문은 `shown_prompts`로 확인할 수 있다. 단 **제안 기록은 화면 표시·녹음·학습 자격을 뜻하지 않는다**(9/29 AI 쪽 피드백). 검증이 필요해지면 `ShownPromptRepositoryPort`에 조회 메서드를 추가한다.
- **FR-7 때 함께 정할 것**: 지금은 한 번 제안한 문장을 다음 추천에서 모두 빼서, 새로고침하면 읽지 않은 문장도 소모된다. 녹음 여부를 알 수 있게 되면 "녹음 안 한 제안 문장을 먼저 다시 주기(이어하기)"를 정한다.
- 문장 풀 버전·해시는 AI 쪽이 제공하면 기록한다(지금은 AI 응답에 없음). 풀 크기는 AI 쪽 파일 3,436항목 중 서버가 문장 단위 과제 코드만 걸러 쓰는 1,807개다.

## 그 외 주의

- 새로 만드는 영속성 어댑터는 `UserPersistenceAdapter.save()`처럼 **JPA 예외를 도메인
  예외로 번역**할 것.
- 도메인이 던지는 `IllegalStateException`/`IllegalArgumentException`은 PR #27의 전역
  핸들러가 각각 409/400으로 매핑한다. 도메인이 `CustomException`/`ErrorCode`를 직접
  참조하게 하지 말 것(도메인 순수성).
- 테스트는 도메인 로직(단위, Mockito 없이)과 application 서비스(Mockito 목) 두 종류로 —
  `RecordingTest`, `UploadDiagnosisRecordingServiceTest` 패턴 참고.
- 6장 코드 컨벤션(Spotless, record 네이밍, MockMvc/SpringBootTest 구분, `@MockitoBean`) 적용.
