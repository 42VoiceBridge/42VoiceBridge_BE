# PR #40 리뷰 반영 계획

- 작성일: 2026-10-03
- 대상: [PR #40 — 개인화 녹음 업로드와 학습 준비 단계 구현](https://github.com/42VoiceBridge/42VoiceBridge_BE/pull/40)
- 분석 기준: `feature/personalization-endpoints`, 커밋 `820d1efc35f3dca263e9031afc4203a83ae2d43c`
- 리뷰 근거: minsui의 2026-10-02 총평 1건. 조회 당시 별도 줄별 코멘트는 없었다.
- 상태: **2026-10-03 사용자 구현 요청에 따라 단계 1~4 구현 및 로컬 검증 완료. 커밋·푸시·리뷰 답변 게시 미실행.**

## 1. 목적과 적용 원칙

명세의 누락된 수정 사항을 복원하고, 개인화 업로드의 정상 요청 호환성과 검증 오류 안내를 개선한다. 정리 작업의 설정·테스트 제어와 녹음 상태 타입도 함께 정리한다.

실제 적용 전 PR 최신 head와 리뷰 추가 여부를 다시 확인한다. 이 문서를 작성한 작업 디렉터리는 `feature/audio-normalization`이므로 구현은 PR 대상 브랜치의 별도 작업 공간에서 진행한다. 기존 작업 디렉터리의 미커밋 변경은 유지한다.

공개 API 변경은 같은 작업에서 `.codex/context/API.md`의 해당 절과 변경 이력에 반영한다. 공통 에러 계약을 변경하면 0.6절도 갱신한다. Notion 반영은 사용자 요청과 페이지 접근이 있을 때 수행한다.

## 2. 항목별 판단

| 항목 | 현재 문제 또는 확인 사항 | 계획상 처리 |
| --- | --- | --- |
| 명세 2.2·2.3·2.5 | 최신 녹음 기준과 오디오 오류 상태에 대한 9/30 수정이 누락됨 | 우선 반영 |
| metadata Content-Type | 문자열 완전 일치로 charset 파라미터가 있는 JSON도 거절 | 호환성 검사로 수정 |
| 검증 메시지 | 기본 메시지만 있어 실패 원인을 구분하기 어려움 | 원인별 메시지 제공 |
| 30일 보관 | 코드·명세에는 존재하나 확인한 자료에 팀·AI 합의 근거가 없음 | 설정 명시·검증 추가, 기간 변경은 합의 필요 |
| 설정 주입·스케줄링 | 필드 주입 및 테스트 컨텍스트 자동 실행 가능성 | 생성자 주입·조건부 스케줄링 적용 |
| 도메인 상태 문자열 | enum 전이 규칙과 도메인 상태 타입이 분리됨 | 도메인 상태를 enum으로 통일 |
| DELETE 빈 202 | 명세에 명시되어 있고 재시도 동작과 부합함 | 유지 권고, 통일 여부는 별도 결정 |

## 3. 단계별 적용 계획

### 단계 1 — 누락된 API 명세 복원

대상: `.codex/context/API.md`

리뷰어가 제시한 다음 문구를 반영한다.

1. 2.2의 `ANALYZED` 설명: `문장마다 가장 최근 녹음이 DONE이 되어 자모 오류 통계에 포함됨`.
2. 2.3의 마지막 문장: `지원하지 않는 오디오 형식에 대한 VALIDATION_FAILED(400, 실사용 인식과 같음)는 오디오 변환 적용 때 함께 구현한다.` 최신 head에 진단 변환이 이미 적용됐다면 미래형 문구 대신 실제 구현 상태를 기록한다.
3. 2.5의 세션 완료·통계 기준: 문장마다 가장 최근 녹음이 `DONE`이어야 완료된다. 재녹음이 처리 중이거나 실패했다면 과거 `DONE`만으로 완료하지 않는다. 통계도 문장별 최신 녹음 하나만 사용하고, 해당 녹음이 무음이면 그 문장은 제외한다.
4. 변경 이력에 다음 두 항목을 복원한다.
   - `2026-09-30 | 2.2, 2.5 | 세션 완료와 통계 모두 문장마다 가장 최근 녹음 기준 (PR #38 리뷰 반영)`
   - `2026-09-30 | 2.3 | 지원하지 않는 오디오 형식: 422(예정) → 400 VALIDATION_FAILED (실사용 인식 PR #39와 맞춤)`

완료 기준: 조회·완료·통계 설명이 같은 최신 녹음 기준을 사용하고, 진단 오디오 오류 설명이 적용 시점의 코드와 일치한다.

### 단계 2 — metadata 호환성과 검증 메시지 개선

대상:

- `src/main/java/com/voicebridge/adapter/in/web/PersonalizationController.java`
- `src/main/java/com/voicebridge/application/UploadPersonalizationRecordingService.java`
- `src/test/java/com/voicebridge/adapter/in/web/PersonalizationRecordingIntegrationTest.java`
- `.codex/context/API.md` 4.1 및 변경 이력

#### 요청 형식

- Content-Type을 `MediaType.parseMediaType(...)`로 파싱하고 `isCompatibleWith(MediaType.APPLICATION_JSON)`로 검사한다.
- 누락·파싱 불가능·비호환 Content-Type은 400 `VALIDATION_FAILED`로 변환한다. 파싱 예외가 500으로 빠지지 않게 한다.
- JSON 파트 계약과 4,096바이트 제한을 유지한다. 빈 파트·잘못된 JSON·객체가 아닌 JSON은 각각 설명 가능한 오류로 반환한다.
- `shownPromptId`와 `promptId`는 문자열 타입과 필수 값을 확인한다. `asText()`의 숫자 등 암묵적 변환에 의존하지 않는다.
- 동의 필드는 JSON boolean만 허용한다. 생략 시 false 규칙과 `storeAudio=true` 필수 조건을 유지한다. `useForTraining=false`는 정상 저장 가능한 요청이다.
- 일반 문자열 `formData.append('metadata', JSON.stringify(meta))`까지 지원하는 것으로 범위를 확대하지 않는다. Content-Type 호환성 수정만으로 해당 전송 방식이 지원되는 것은 아니다.

명세에는 다음 전송 예시를 추가한다.

```javascript
const formData = new FormData();
formData.append(
  'metadata',
  new Blob([JSON.stringify(meta)], { type: 'application/json' }),
);
formData.append('audioFile', audioFile);
// 요청 전체의 Content-Type은 브라우저가 boundary와 함께 설정하도록 둔다.
```

JSON 파트 유지 이유는 동의 값의 boolean 타입을 명확하게 검증하기 위해서라고 설명한다. 일반 폼 필드 지원이 필요하면 별도 계약 변경으로 다룬다.

#### 오류 메시지

기존 `CustomException(ErrorCode, String)`을 사용한다. HTTP 상태와 `error.code`는 유지하며, 컨트롤러는 요청 형식 오류를, 서비스는 저장 동의 및 추천 기록과의 일치 조건을 설명한다.

| 조건 | 메시지 예시 |
| --- | --- |
| metadata Content-Type 오류 | `metadata의 Content-Type은 application/json이어야 합니다.` |
| 빈 metadata | `metadata가 비어 있습니다.` |
| metadata 크기 초과 | `metadata는 4096바이트 이하여야 합니다.` |
| JSON 구문·구조 오류 | `metadata는 올바른 JSON 객체여야 합니다.` |
| 필수 ID 누락·형식 오류 | `shownPromptId는 유효한 UUID 문자열이어야 합니다.` |
| promptId 누락·타입 오류 | `promptId는 비어 있지 않은 문자열이어야 합니다.` |
| 동의 타입 오류 | `storeAudio는 boolean이어야 합니다.` / `useForTraining은 boolean이어야 합니다.` |
| 저장 동의 없음 | `녹음 저장에 동의해야 합니다: storeAudio=true` |
| 추천 문장 불일치 | `promptId가 추천 기록의 문장과 일치하지 않습니다.` |
| 빈 오디오 | `audioFile이 비어 있습니다.` |

메시지 문구는 구현 시 기존 프로젝트 표현과 맞춘다. 필수 파트 자체의 누락은 기존 공통 예외 처리와 중복되지 않게 확인한다. 소유권 검사를 문장 불일치 검사보다 먼저 수행하는 순서를 유지한다.

완료 기준: charset 포함 JSON 업로드 성공, 형식·동의·문장 불일치 오류의 구체적 메시지 제공, 타인 추천 기록 403 및 없는 기록 404 유지.

### 단계 3 — 보관 설정과 스케줄링 제어

대상:

- `src/main/resources/application.yml`
- `src/main/java/com/voicebridge/application/PersonalizationUploadCleanup.java`
- `src/main/java/com/voicebridge/VoiceBridgeApplication.java`
- 신규 개인화 설정·스케줄링 설정 클래스: 기존 설정 패키지 관례를 확인하여 위치 결정
- 테스트 설정 및 정리 작업 테스트

적용 내용:

1. `voicebridge.personalization.retention-days`의 기본값 30과 `cleanup-delay-ms`의 기본값 300000을 YAML에 명시한다.
2. 개인화 설정을 생성자로 전달하고, 보관 일수와 실행 간격의 양수 조건을 시작 시 검증한다. 설정 바인딩 방식은 저장소의 기존 관례에 맞춘다.
3. `@EnableScheduling`을 애플리케이션 클래스에서 전용 설정으로 이동한다. 기존 다른 예약 작업 유무를 확인해 비활성화 범위를 결정한다.
4. 명시적 활성화 프로퍼티 조건을 둔다. 테스트는 이 조건을 false로 설정하고, 운영 기본 동작은 활성화로 유지한다. 설정 클래스 이동만으로 테스트에서 꺼졌다고 판단하지 않는다.
5. 정리 로직은 테스트에서 직접 호출할 수 있게 유지한다. 실패 업로드 정리와 만료 녹음 삭제가 모두 수행되는 현재 책임을 고려한다.

보관 정책의 결정 사항:

- 30일이 합의된 기간인지, 계산 기준이 현재 코드의 `createdAt`인지 확인한다.
- 학습 API가 503인 동안 수집한 녹음도 같은 기간으로 삭제되는지 확인한다.
- 확인 전 기간을 연장하거나 자동 삭제를 중지하지 않는다. 특히 만료 삭제를 중지하려고 정리 작업 전체를 끄면 실패 업로드 파일 정리도 중단된다.
- 기간이 변경되면 API 4.1, 운영 설정, 학습 후보 판정 기준을 함께 맞춘다. 설정값 변경이 기존 녹음에도 적용되는지 문서에 설명한다.

완료 기준: 유효하지 않은 설정은 시작 시 거절되고, 일반 통합 테스트에서는 예약 실행이 발생하지 않으며, 정리 작업 직접 실행 테스트는 통과한다.

### 단계 4 — 도메인 녹음 상태를 enum으로 통일

대상:

- `src/main/java/com/voicebridge/domain/personalization/PersonalizationRecording.java`
- `src/main/java/com/voicebridge/domain/personalization/PersonalizationRecordingStatus.java`
- `src/main/java/com/voicebridge/adapter/out/persistence/PersonalizationRecordingJpaEntity.java`
- `src/main/java/com/voicebridge/adapter/out/persistence/PersonalizationRecordingPersistenceAdapter.java`
- 녹음 생성·상태 비교를 사용하는 관련 테스트

적용 내용:

1. 도메인의 `String status`를 `PersonalizationRecordingStatus`로 변경한다.
2. 준비 상태 생성과 학습 후보 판정을 enum 상수로 표현한다.
3. DB 문자열과 enum 변환을 persistence 경계에 모은다. 기존 DB 상태 문자열과 공개 응답 문자열은 유지한다.
4. 기존 조건부 DB 갱신과 전이 규칙을 유지한다. enum 도입을 이유로 DB에서 보장하던 경합 방지를 메모리 검사로 대체하지 않는다.

완료 기준: 도메인 상태 문자열 비교가 제거되고, 저장·조회 변환과 삭제 또는 정리 이후 늦은 업로드의 공개 방지 동작이 유지된다.

### 단계 5 — DELETE 응답 및 리뷰 설명 정리

`DELETE /personalization/recordings/{recordingId}`는 현재 명세 4.1.1의 본문 없는 202를 유지하는 것을 권고한다. 삭제 실패 시 재시도하므로 삭제 접수를 의미하는 202가 적절하다.

팀이 공통 envelope로 통일하기로 결정하면, 별도 변경으로 컨트롤러 반환 타입·API 4.1.1·변경 이력·응답 본문 테스트를 함께 수정한다. 결정 전에는 응답 형식을 변경하지 않는다.

리뷰어가 수행한 로컬 MySQL DDL 적용, `ddl-auto: validate` 기동, 활성 job 고유 제약 검증은 리뷰어의 검증 결과로 기록한다. 이를 직접 재검증한 결과 또는 S3·실제 AI serving 검증으로 표현하지 않는다.

## 4. 검증 계획

아래 항목은 구현 후 수행할 계획이며, 이 문서 작성 과정에서는 실행하지 않는다.

| 범위 | 확인할 시나리오 |
| --- | --- |
| 정상 업로드 | application/json 및 charset 포함 JSON 성공, useForTraining=false 저장 가능 |
| 잘못된 metadata | Content-Type 누락·비호환·잘못된 문법, 빈 파트, 4096바이트 초과, JSON 구문 오류·배열·null |
| 필드 검증 | ID 누락·타입 오류·잘못된 UUID, boolean 대신 문자열·숫자·null, 저장 동의 false 또는 생략 |
| 업무 규칙 | promptId 불일치 400과 메시지, 타인 추천 기록 403, 없는 추천 기록 404 |
| 파일 및 부작용 | 빈 음성 400, 검증 거절 시 정규화·저장 미실행 |
| 설정 | 보관 일수·실행 간격의 0 또는 음수 거절, 테스트에서 예약 실행 비활성 |
| 정리 | 만료 전 유지·만료 후 삭제, 저장소 삭제 실패 후 재시도, 기존 실패 업로드 정리 유지 |
| 상태 | enum 저장·조회 왕복, 학습 후보 판정, 잘못된 전이 및 늦은 업로드 공개 방지 |
| 계약 | 구현 HTTP 상태·error.code·응답 필드와 API 명세 일치, DELETE 본문 없는 202 유지 |

먼저 변경된 컨트롤러·서비스·도메인·persistence 관련 테스트와 포맷 검사를 수행한다. 이후 저장소에서 요구하는 전체 테스트·CI 검사를 수행한다. FFmpeg 등 외부 도구가 필요한 검사와 실제 MySQL·S3·AI 검증은 실행 환경 및 변경 영향에 따라 별도로 결과를 명시한다.

## 5. 완료 조건과 남는 결정

- 단계 1~4 구현과 관련 테스트가 완료되고 API 명세가 같은 변경에 포함된다.
- PR 제출 전 구현의 HTTP 상태와 오류 코드를 명세와 대조한다.
- 30일 보관 정책의 합의 여부와 DELETE 응답 유지 여부를 리뷰 답변에 명확히 설명한다. 미확정 정책은 완료로 표시하지 않는다.
- JSON Blob 요청 예시와 원인별 오류 안내가 프론트에서 사용할 수 있는 수준으로 문서화된다.
- 실제 구현 요청을 받은 후에만 코드 수정과 검증을 시작한다. 리뷰 답변 게시·커밋·푸시는 이 계획 문서 작성 범위에 포함하지 않는다.


## 6. 2026-10-03 구현 및 검증 결과

- 구현 작업 공간: `/private/tmp/voicebridge-personalization-20261002`, 브랜치 `feature/personalization-endpoints`. PR 최신 head가 분석 기준 커밋과 같음을 확인했다.
- API 2.2·2.3·2.5와 누락된 변경 이력을 복원했다. 대상 브랜치에서 진단 오디오 변환은 아직 미적용이므로 2.3의 예정 표현을 유지했다.
- JSON Content-Type 파라미터 허용, 크기·객체·문자열 필드·동의 타입 검사, 정규 UUID 검사 및 후행 JSON 값 거절을 적용했다. 원인별 검증 메시지와 Blob 예시를 API 4.1에 기록했다.
- 보관 일수·실행 간격을 생성자로 주입하고 양수 검증을 추가했다. YAML에 기본값과 환경변수, 스케줄링 활성화 조건을 명시했다. 현재 예약 작업은 개인화 정리 작업뿐이며 테스트에서는 자동 실행을 비활성화한다.
- 도메인 녹음 상태를 enum으로 통일했다. DB 문자열 변환과 조건부 DB 전이 규칙은 유지했다.
- 30일 보관 및 본문 없는 DELETE 202를 유지했다. 보관 기간의 팀·AI 합의는 여전히 확인이 필요하다.
- 최종 검증: Java 17로 `./gradlew spotlessApply build audioIntegrationTest --offline` 성공. 일반 테스트 370개, 실제 FFmpeg 오디오 통합 테스트 18개에서 실패·오류·건너뜀 0개. `git diff --check` 통과.
- 오류 메시지·charset 요청·예약 실행 비활성화·만료 조회·상태 왕복·삭제 재시도·정리 claim 실패 시 파일 보존을 검증했다.
- 실제 MySQL·S3·AI serving 검증 및 CI의 Docker 이미지 검증은 이번 로컬 검증에 포함하지 않았다. 리뷰어의 기존 MySQL 검증과 이번 직접 실행 결과를 구분한다.
- 원래 작업 디렉터리의 기존 미커밋 코드 변경은 보존했다. 구현은 별도 작업 공간에 있으며 커밋·푸시·리뷰 답변 게시를 수행하지 않았다.
