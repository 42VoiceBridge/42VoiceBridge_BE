# 개인화 학습 도메인 예외 분리 — PR #25 리뷰 반영 메모

작성일: 2026-09-23
작업 브랜치: `feature/personalization-job-validation`
상태: PR #25 리뷰에 따라 범위 밖 변경을 별도 커밋으로 분리했다. 최신 전체 빌드 결과는 아래 리뷰 반영 내역에 기록한다.

## 변경 배경

학습 작업 생성 검증과 종료된 작업의 상태를 보호하는 규칙은 Domain에 있어야 한다. 그러나 PR #25의 `PersonalizationJob`이 사용하는 `CustomException`은 `ErrorCode`를 참조하고, `ErrorCode`는 Spring의 `HttpStatus`를 참조한다. 따라서 도메인에 HTTP·프레임워크에 대한 간접 의존성이 생겼다.

리뷰 반영의 목적은 검증을 Service로 옮기는 것이 아니다. 업무 규칙은 도메인에 유지하고, 예외를 HTTP 상태 및 API 오류 코드로 번역하는 책임만 `GlobalExceptionHandler`에 둔다.

## 현재 반영된 구조

| 상황 | 도메인 예외 | 웹 응답 |
| --- | --- | --- |
| 잘못된 상태 전이 | `IllegalStateException` | 409 / `INVALID_STATE_TRANSITION` |
| 사용자·모델 정보·실패 사유 누락 | `IllegalArgumentException` | 400 / `VALIDATION_FAILED` |
| 학습 녹음 수 부족 | `InsufficientRecordingException` | 422 / `INSUFFICIENT_RECORDINGS` |

- `PersonalizationJob`에서 `CustomException`과 `ErrorCode` import를 제거했다.
- `InsufficientRecordingException`은 도메인 패키지에 두고 Java의 `RuntimeException`만 상속한다.
- 400·409 범용 핸들러는 develop의 PR #27에서 가져온 것이다. 사용자가 녹음 부족 전용 422 핸들러를 추가했다.
- 학습 녹음 최소 개수 5개는 임시 정책이다. 실제 녹음 수 조회와 AI 학습 연결은 후속 작업이다.
- `PENDING` 또는 `IN_PROGRESS`에서만 실패 전이를 허용한다. 종료된 작업의 상태·모델 정보·실패 사유·종료 시각을 보호한다.

## 예외 전파 흐름

```text
Controller → Service → PersonalizationJob.create(userId, count)
                              ↓ 녹음 부족
                    InsufficientRecordingException
                              ↓ 호출자가 처리하지 않으면 웹 계층으로 전파
                    GlobalExceptionHandler
                              ↓
                    HTTP 422 + ApiResponse.error
```

이 자동 응답 변환은 Spring MVC 요청 처리 과정에 적용된다. 도메인 메서드를 직접 호출하는 단위 테스트나 별도 비동기 작업에서 핸들러가 자동 호출되는 것은 아니다.

## 이번에 작성·수정한 테스트

- `PersonalizationJobTest` (19건): 기존 CustomException/errorCode 검사를 실제 도메인 예외 타입 검사로 변경. 생성 경계값, 허용·금지 상태 전이, 잘못된 입력 시 상태 보존을 검증한다. 완료 모델 경로 및 종료된 작업의 모델 경로 보존 검증을 보강했다.
- `GlobalExceptionHandlerTest` (3건): 기존 400·409 테스트를 유지하고 422 상태·오류 코드·메시지·실패 응답 구조 검증을 추가했다.
- `GlobalExceptionHandlerMvcTest` (4건): standalone MockMvc와 테스트 전용 Controller로 실제 도메인 예외를 발생시킨다. 구체적 예외에 맞는 422·409·400 핸들러가 선택되는지, 일반 RuntimeException은 여전히 500으로 처리되는지 확인한다. 실제 학습 API를 추가한 것은 아니다.

관련 회귀 테스트로 `DiagnosisSessionTest` 3건, `RecordingTest` 10건, `GetPersonalizationModelServiceTest` 2건을 함께 실행했다.

**합계 41건 통과, 실패·오류 0건.** 전체 테스트 스위트를 실행한 것은 아니다. 이번에 작성·수정한 테스트 파일 3개의 Spotless 검사도 통과했다. 후속 메시지 정리에서 도메인 파일 두 개의 포맷 검사와 도메인·핸들러 테스트 26건도 통과했다. 핸들러 운영 파일의 포맷 검사는 별도로 남아 있다.

재실행 명령:

```bash
./gradlew test --tests '*PersonalizationJobTest' --tests '*GlobalExceptionHandlerTest' --tests '*GlobalExceptionHandlerMvcTest' --tests '*DiagnosisSessionTest' --tests '*RecordingTest' --tests '*GetPersonalizationModelServiceTest'
```

## 후속 작업 — 예외 메시지 정리

- 사용자 ID, 학습 시작·완료·실패 조건, 모델 정보 및 실패 사유 누락을 자연스러운 한국어로 안내하도록 메시지를 정리했다. 예외 타입과 검증 조건은 유지했다.
- 녹음 부족 응답에서 개발 메모를 제거했다. 최소 5개 임시 정책은 기존 도메인 상수의 주석과 이 문서에 남겨두었다.
- `PersonalizationJobTest` 19건, `GlobalExceptionHandlerTest` 3건, `GlobalExceptionHandlerMvcTest` 4건을 재실행해 총 26건 통과했다. 수정한 도메인 파일 두 개의 Spotless 검사도 통과했다.

## PR #25 추가 리뷰 반영

- 2026-09-23 `./gradlew build` 전체 실행: **BUILD SUCCESSFUL**. 테스트 78건, 실패 0건, 오류 0건, 건너뜀 0건.
- `GlobalExceptionHandler.java`를 포함한 전체 `spotlessCheck` 통과. 추가 포맷 수정은 필요하지 않았다.
- 첫 실행은 Docker 미실행으로 Redis 통합 테스트 초기화가 실패했다. Docker Desktop 실행 후 테스트 제외 없이 전체 빌드를 재실행해 통과했다.

- `PersonalizationController.java`를 develop 기준으로 복원하고 `PROJECT_MEMORY.md`를 PR 변경분에서 제외했다.
- 제외한 원본 변경은 `feature/personalization-review-followup` 브랜치의 `0dd5de9` 커밋으로 분리했다. 컨트롤러 주석 정리와 프로젝트 메모만 포함한다.
- 일반 Java 예외 핸들러의 적용 범위는 이번 리뷰에서 수용되었으므로 동작을 유지한다. 예외 사용 규칙의 프로젝트 지침 반영은 리뷰어가 별도로 진행한다.

## 검토 시 유의점

범용 `IllegalArgumentException`/`IllegalStateException` 핸들러는 도메인 외부에서 발생한 동일 타입의 예외에도 적용될 수 있다. 별도 식별이 필요한 업무 실패는 프레임워크에 의존하지 않는 전용 도메인 예외로 구분한다.
