# 문구 TTS 전환 계획서 — 개인화 중단, 상황별 문구 말하기 추가

작성 2026-10-30 · 상태: **초안(팀 확인 전)** · 코드는 아직 한 줄도 바꾸지 않았다.
이 문서는 새 문서다. 기존 문서(PROGRESS, 명세서 등)는 건드리지 않았고, 구현 후 갱신할 목록은 9장에 있다.

## 0. 한 줄 요약

개인화는 켜지 않는다(코드는 보존). 대신 "병원·식당 같은 상황의 문구를 누르면 클로바 TTS가 대신 말해주는" 기능을 백엔드에 추가한다. 사용자는 자기 문구를 추가·삭제할 수 있다. 이 기능은 AI 서버 없이 끝까지 동작한다.

## 1. 배경과 결정

- 개인화(녹음 → 학습 → 내 모델)는 AI 서버 배포, 학습 시간, 데이터 정책이 모두 걸려 있어 이번 범위에서 제외한다.
- 진단 세션 · 자모 오류 통계 · 추천 문장은 개인화 녹음을 모으기 위한 흐름이었다. 개인화가 빠지면 목적이 사라지고 AI 서버가 없으면 배포 환경에서 503이 되므로 같이 끈다(코드는 보존).
- AI 레포(`42VoiceBridge_AI`)는 읽기 전용이고 담당이 다르다. 우리가 고치는 것은 백엔드뿐이다.

| 흐름 | 상태 | AI 서버 필요 |
| --- | --- | --- |
| ① 말하기 → 인식 → 내가 확인 → TTS | 이미 구현. **유지** | 필요(배포 전엔 인식 실패) |
| ② 문구 목록 → 누르면 TTS | **신규** | 불필요 |
| ③ 개인화 / 진단 / 자모 통계 / 추천 | **끔(코드 보존)** | — |

## 2. 범위

**한다**
1. 개인화·진단 계열 엔드포인트를 설정 플래그로 끈다.
2. 문구(Phrase) 기능을 만든다: 목록, 추가, 삭제, 말하기(TTS).
3. 기본 제공 문구를 시드로 넣는다.

**하지 않는다 (YAGNI)**
- 문구 수정(edit): 삭제 후 재추가로 대신한다. 텍스트가 바뀌지 않으면 오디오 캐시가 낡을 일이 없다.
- 사용 횟수 집계, 즐겨찾기, 사용자 정의 카테고리, 목소리 선택.
- 개인화 코드 삭제. 삭제는 나중에 별도 PR로 한다.

## 3. 1단계 — 기능 끄기 (PR 1)

**방식: 주석 처리 대신 설정 플래그를 쓴다.** 코드를 주석으로 막으면 그 코드를 쓰는 테스트가 컴파일부터 깨지고, 나중에 되살릴 때 diff가 지저분하다. `@ConditionalOnProperty`는 컨트롤러 빈이 안 생기게 해서 같은 효과(엔드포인트 404)를 내고 설정 한 줄로 되돌릴 수 있다.

| 플래그 (기본값 false) | 끄는 대상 |
| --- | --- |
| `voicebridge.features.personalization` | `PersonalizationController` (녹음 업로드·삭제, train, model, train 상태) |
| `voicebridge.features.diagnosis` | `DiagnosisSessionController`, `JamoErrorStatsController`, `RecommendationController` |

확인된 사실 (코드 조사)
- 개인화 코드는 잘 분리돼 있다. 개인화 밖에서 개인화 타입을 쓰는 곳은 `RecognizeSpeechService`(활성 어댑터 조회) 하나뿐이다. 어댑터가 없으면 항상 `BASE_ADAPTED`가 되므로 ①은 그대로 동작한다.
- `PersonalizationSchedulingConfig`는 이미 `scheduling-enabled` 토글이 있다. 정리 스케줄러(`PersonalizationUploadCleanup`)가 이 토글만으로 멈추는지는 **구현 때 확인**한다.
- `GlobalExceptionHandler`가 `InsufficientRecordingException`을 참조하고 `ErrorCode`에 개인화 코드가 있지만 무해하므로 그대로 둔다.
- 인증이 필요한 매핑 없는 경로는 404로 응답한다(`ClientErrorResponseIntegrationTest`로 이미 검증된 동작).

주의
- 기존 개인화·진단 통합 테스트가 깨지지 않도록 `src/test/resources/application.yml`에 두 플래그를 `true`로 둔다. 테스트 설정이 main 설정을 통째로 대체하기 때문이다.
- 플래그가 꺼진 기본 상태에서 해당 엔드포인트가 404인지 확인하는 테스트를 각각 추가한다.
- DB 테이블과 Flyway V1은 그대로 둔다(테이블이 남아 있어도 문제없다).

## 4. 2단계 — 문구 기능 설계 (PR 2, 3)

### 4.1 API (모두 JWT 필요, 경로는 `/api/v1`)

| 메서드 | 경로 | 설명 | 성공 |
| --- | --- | --- | --- |
| GET | `/phrases?category=` | 기본 문구 + 내 문구. category 생략 시 전체 | 200 |
| POST | `/phrases` | 내 문구 추가 `{category, text}` | 201 |
| DELETE | `/phrases/{phraseId}` | 내 문구 삭제. 기본 문구는 403 | 204 |
| POST | `/phrases/{phraseId}/speak` | 문구를 TTS로 말하기 → 재생 URL 반환 | 200 |
| GET | `/phrases/{phraseId}/audio` | **local 프로파일 전용** 재생 (기존 `LocalTtsAudioController`와 같은 방식) | 200 |

응답은 모두 DTO + 공통 `ApiResponse`. 응답 항목: `id`, `category`, `text`, `isDefault`.

### 4.2 헥사고날 구성 (신규 파일)

- `domain/phrase/`: `Phrase`, `PhraseCategory`(enum)
- `port/in/`: `ListPhrasesUseCase`, `AddPhraseUseCase`, `DeletePhraseUseCase`, `SpeakPhraseUseCase`
- `port/out/`: `PhraseRepositoryPort` (TTS 엔진·저장소·재생 URL은 기존 `TtsEnginePort`, `StoragePort`, `AudioPlaybackUrlPort` 재사용)
- `application/`: 유스케이스별 Service (Create/Update 같은 섣부른 공통화 없이 각자 독립)
- `adapter/in/web/`: `PhraseController`, `LocalPhraseAudioController`(local), `dto/`
- `adapter/out/persistence/`: `PhraseJpaEntity`, `PhraseJpaRepository`, `PhrasePersistenceAdapter`, `PhraseSeeder`
- `AudioPlaybackUrlPort`에 문구용 URL 메서드 추가: S3는 기존 presign 재사용, local은 `/api/v1/phrases/{id}/audio`. 지금 시그니처는 `ttsId`로 경로를 만들어서 문구에는 못 쓴다.

도메인 규칙(상태 변경은 Service가 아니라 도메인이 담당): `Phrase.createForUser(...)`가 텍스트 검증을 하고, `phrase.attachAudio(key)`는 이미 오디오가 있으면 무시한다. 검증 기준값은 도메인에만 둔다(SSOT).

### 4.3 DB — 새 마이그레이션 `V2__phrases.sql`

`phrases`: `id BINARY(16)`, `owner_id BINARY(16) NULL`(NULL이면 기본 제공), `category VARCHAR`, `text VARCHAR(200)`, `text_sha256 CHAR(64)`, `audio_storage_key VARCHAR(255) NULL`, `created_at DATETIME(6)`.
- 인덱스는 조회 패턴 기준: `(owner_id, category)` — "내 문구 + 카테고리" 조회.
- enum은 MySQL enum이 아니라 VARCHAR(V1 주석 규칙).
- 중복 방지: `(owner_id, category, text_sha256)` 유니크. MySQL은 NULL이 서로 다르다고 보므로 기본 문구의 중복은 시더가 존재 여부로 막는다.
- V1은 고치지 않는다. 로컬은 `ddl-auto: update`, 테스트는 `create-drop`이라 V2는 운영에서만 적용된다. `FlywayMigrationTest`에 V2 검증을 추가한다.

### 4.4 speak 동작

1. 문구 조회 → 기본 문구이거나 내 것인지 확인(아니면 403/404).
2. `audio_storage_key`가 있으면 바로 재생 URL 반환. **캐시 적중, 클로바 호출 없음.**
3. 없으면 `TtsEnginePort.synthesize(text)` → `StoragePort.upload` → `phrase.attachAudio(key)` 저장 → URL 반환.
4. 클로바 실패는 새 에러코드 503으로 응답하고 **캐시에 저장하지 않는다.**

설계 포인트
- 외부 호출(클로바)을 `@Transactional` 안에서 하지 않는다(커넥션 점유 방지). 조회와 저장만 각각 짧은 트랜잭션으로 둔다.
- 같은 문구를 동시에 처음 누르면 두 번 합성될 수 있다. 먼저 저장된 키만 채택하고, 진 쪽이 올린 오브젝트는 삭제한다. 기본 문구는 모든 사용자가 같은 오디오를 쓰므로 한 번만 합성된다.
- 기존 TTS 흐름(①: `TtsRequest`, `Confirmation`)은 건드리지 않는다. 문구는 사용자가 저장한 텍스트가 곧 확인된 텍스트다.

### 4.5 검증과 보안 규칙

- 텍스트: 앞뒤 공백 제거, 비어 있으면 거부, 제어문자 거부, 길이 상한은 **정책값 100자**(클로바 실제 상한은 구현 때 문서로 확인).
- 사용자당 내 문구 상한 100개(정책값).
- 소유권: 내 문구는 본인만 조회·삭제. 기본 문구는 읽기 전용.
- 외부 입력은 DTO 검증 + JPA 파라미터 바인딩.
- **사용자가 쓴 문구는 외부 서비스(네이버 클로바)로 전송된다.** 병명이나 주소 같은 개인정보가 들어갈 수 있으므로 민감정보로 취급하고, 앱에 "입력한 문구는 음성 합성을 위해 외부 서비스로 전송됩니다" 고지가 필요하다. 로그에 문구 텍스트를 남기지 않는다.

### 4.6 에러코드 (추가)

`DUPLICATE_PHRASE`(409), `PHRASE_LIMIT_EXCEEDED`(409), `TTS_UNAVAILABLE`(503). 그 외는 기존 `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`, `FORBIDDEN_ACCESS`를 재사용한다.

### 4.7 기본 문구 초안 (우리가 직접 쓴 문장, 팀 확정 필요)

AI-Hub 명령어 데이터는 안심존 데이터라 문장을 가져오지 않는다. 카테고리 아이디어만 참고했다.

| 카테고리 | 초안 |
| --- | --- |
| 병원 | 접수하고 싶어요. / 여기가 아파요. / 약을 처방받고 싶어요. / 천천히 말씀해 주세요. |
| 식당 | 메뉴판 주세요. / 이거 주세요. / 물 좀 주세요. / 포장해 주세요. / 계산해 주세요. |
| 약국 | 처방전 여기 있어요. / 이 약은 어떻게 먹나요? |
| 마트·편의점 | 이거 얼마예요? / 봉투 주세요. |
| 교통 | 여기서 내려 주세요. / 이 버스 ○○ 가나요? |
| 일상·긴급 | 도와주세요. / 잠시만 기다려 주세요. / 말이 느려서 천천히 들어 주세요. |

시드는 `SentenceSeeder`처럼 `ApplicationRunner`로 넣고, 이미 있으면 아무것도 하지 않는다(멱등). Flyway SQL로 넣지 않는 이유: 바이너리 UUID 리터럴이 Hibernate 인코딩과 어긋날 수 있고(PROGRESS 10번), 로컬은 Flyway를 쓰지 않는다.

## 5. 테스트 계획

- 도메인 단위: `Phrase` 텍스트 검증(공백, 제어문자, 길이), `attachAudio` 중복 무시.
- 영속성: 실제 DB(Testcontainers MySQL)로 저장·조회·유니크 제약, 시더 멱등성.
- 통합(H2 + MockMvc): 목록(기본 + 내 것), 추가 201, 중복 409, 상한 409, 삭제 204.
- **우회 차단을 따로 증명** (CLAUDE.md 6장): 다른 사용자의 문구 삭제·말하기 거부, 기본 문구 삭제 거부, 미인증 401.
- speak: 두 번 눌러도 합성은 1회(`TtsEnginePort`를 `@MockitoBean`으로 두고 호출 횟수 검증), 클로바 실패 시 503이며 캐시 미저장.
- 1단계: 플래그가 꺼진 기본 상태에서 대상 엔드포인트가 404.
- Mock 테스트는 클로바 실제 동작을 보장하지 않는다. 실제 키로 한 번 수동 왕복 검증을 별도로 한다.

## 6. PR 분할·순서·담당

Atomic PR 원칙에 따라 기능 변경과 정리를 섞지 않는다. 포맷은 `./gradlew spotlessApply test`로 맞춘다.

| 순서 | 브랜치(안) | 내용 | 크기 | 담당(안) |
| --- | --- | --- | --- | --- |
| 1 | `chore/disable-personalization-diagnosis` | 플래그 2개 + 테스트 설정 + 404 테스트 | S | 팀장 |
| 2 | `feat/phrase-crud` | 도메인·포트·DTO(계약), V2, 시더, **목록 유스케이스 1개 완성(레퍼런스)**, 추가·삭제 | M | 계약과 목록은 팀장, 추가·삭제는 백엔드 A |
| 3 | `feat/phrase-speak` | speak, 오디오 캐시, 재생 URL 포트 확장, local 컨트롤러 | M | 백엔드 B (클로바 실검증 포함) |
| 4 | `docs/phrase-pivot` | 문서 갱신(9장) | S | 팀장 |
| 후속 | `chore/remove-personalization` | 개인화 코드·테스트·테이블 삭제 여부 판단 후 진행 | L | 별도 결정 |

## 7. 결정이 필요한 것 (이 계획의 가정)

| # | 질문 | 이 계획의 가정 |
| --- | --- | --- |
| D1 | 기본 카테고리와 문구 목록 확정 | 4.7 초안으로 진행, 기획 확정 시 교체 |
| D2 | 사용자가 카테고리를 직접 만들 수 있나 | 아니오. 고정 카테고리 안에 문구만 추가 |
| D3 | 문구 수정 기능 | v1 제외. 삭제 후 재추가 |
| D4 | 진단·자모 통계·추천도 같이 끄나 | 예. 플래그 `diagnosis`로 묶음 |
| D5 | 개인화 코드 삭제 시점 | 새 기능 이후 별도 PR |

## 8. 리스크

- 클로바 TTS는 **실제 키로 한 번도 검증하지 못했다**(WireMock 계약 테스트까지). 키 확보가 PR 3의 선행 조건이다.
- S3 어댑터도 실검증 전이다. 로컬 파일 어댑터로만 확인했다. 배포 환경 재생은 인프라 프로비저닝 이후에 확인된다.
- 클로바 호출 지연과 비용: 오디오 캐시로 완화하지만 첫 클릭은 1~2초 걸릴 수 있다(예상치, 측정 전).
- ①(말하기 → 인식)은 AI 서버가 배포돼야 배포 환경에서 동작한다. 이 계획과 무관하게 남는 의존성이다.
- CORS `allowedOriginPatterns("*")` + `allowCredentials(true)` 조합은 운영 배포 전 재검토가 필요하다(기존 이슈).

## 9. 구현 후 갱신할 문서 (지금은 수정하지 않음)

- Notion: 서비스 기획, 요구사항 명세서, API 명세서(문구 4개 엔드포인트 추가, 개인화·진단 비공개 표기), 코드 컨벤션 변경 없음
- `CLAUDE.md` 1장 "핵심 아이디어"(Whisper 개인화 서술을 현재 방향으로)
- `docs/PROGRESS.md`, `README.md`

## 10. 완료 체크리스트 (CLAUDE.md 10장)

- [ ] 변경 이유 요약
- [ ] 단위·통합 테스트 통과, 클로바 실제 왕복은 수동 검증 기록
- [ ] 보안: 소유권 우회 차단 테스트, 개인정보 고지, 로그에 텍스트 미기록
- [ ] 성능: 캐시 적중 시 클로바 미호출, 외부 호출은 트랜잭션 밖
- [ ] 설계 원칙: Inbound Port 의존, SSOT(검증값은 도메인에만), 외부 예외 번역, `@Version` 불필요 확인
- [ ] 컨벤션: Spotless, record 접근자, `@MockitoBean`
- [ ] 위 9장 문서 갱신
