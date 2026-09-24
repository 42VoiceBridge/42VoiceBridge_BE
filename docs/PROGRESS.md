# 작업 진행 기록

> 매 작업이 끝날 때마다 이 문서를 갱신한다. 새 대화를 시작할 때 이 문서부터 읽으면 이전 지시사항이 어떻게 끝났는지 복사-붙여넣기 없이 파악할 수 있다.

마지막 업데이트: 2026-09-24

## 최신 작업 — PR #25 도메인 예외 분리 검증 (2026-09-23)

- 사용자가 변경한 순수 Java 도메인 예외에 맞춰 `PersonalizationJobTest`를 수정하고, 녹음 부족 422 핸들러 단위 테스트 및 MVC 예외 선택 테스트를 추가했다.
- 관련 6개 테스트 클래스, 총 41건 통과. 변경한 테스트 파일의 Spotless 검사 통과. 전체 테스트 스위트는 실행하지 않았다.
- 후속 요청으로 도메인 예외 메시지와 두 도메인 파일의 포맷을 정리하고 관련 테스트 26건을 재검증했다. 핸들러 운영 파일 포맷 검사와 최종 스테이징, PR 커밋·푸시는 남아 있다.
- 상세 변경 이유, 예외 전파 흐름, 검증 범위와 후속 작업은 [개발 메모](./DEV-NOTE-personalization-domain-exceptions.md)에 기록했다.
- 아래 상태 요약과 과거 기록은 해당 작업 시점 기준이다.

## 최신 작업 — 개인화 학습 작업 상태 조회 (2026-09-18)

- `feature/personalization-training-status`에서 `GET /api/v1/personalization/train/{jobId}` 구현. 최신 `develop`을 기준으로 Draft PR을 준비한다.
- `GetPersonalizationTrainingStatusService`가 기존 저장소 포트로 작업을 조회하고 본인 소유를 확인한 뒤 결과를 반환한다. Controller는 전용 `PersonalizationTrainingStatusResponse`와 공통 `ApiResponse`로 응답한다.
- 없는 작업은 404 `RESOURCE_NOT_FOUND`, 타인 작업은 403 `FORBIDDEN_ACCESS`. 잘못된 UUID 등 요청 인자 타입 변환 실패를 400 `VALIDATION_FAILED`로 처리하도록 공통 예외 처리기를 확장했다.
- 인터페이스 이름에 붙어 있던 `rms`를 제거해 파일명과 일치시켰다. 공통 예외 처리기에 있던 기존 주석 수정은 보존했다.
- H2 DB와 MockMvc를 연결한 통합 테스트 8개 추가: 네 상태(PENDING/IN_PROGRESS/COMPLETED/FAILED), 없는 작업, 타인 작업, 잘못된 UUID, 미인증 요청. 테스트 데이터 저장 후 flush/clear로 실제 DB 조회를 검증하고 트랜잭션 롤백으로 격리한다.
- `./gradlew spotlessApply test` 성공: 전체 테스트 22개 통과. `./gradlew build`도 Spotless 검사 포함 성공.
- 조회는 기본 키로 작업 한 건을 읽으며 AI나 S3를 호출하지 않는다. 실제 학습에 따른 상태 갱신과 진행률 전달은 후속 작업이다.
- `docs/NEXT-STEPS-personalization-recognition.md`에 완료 범위 반영. 팀 Notion 명세는 이번 작업에서 갱신하지 않았다.

## 지금 상태 요약

- `main`: `2b21d4a` — `.gitignore`, `CONTRIBUTING.md`만 반영된 상태. 실제 코드 없음.
  - `develop`: `7a1f937` — 헥사고날 스캐폴딩 + 패키지 `com.voicebridge` 리네임 + gradle wrapper + 테스트 H2 데이터소스 분리 + 인증 도메인(PR #5) + Spotless/Jacoco 코드 품질 툴링(PR #6) + 진단세션 계약(PR #7) + 개인화/인식 계약(PR #8) + **JPyRust 기반 AI 연동(PR #9)** + **sentences 시드 데이터(PR #10)** + **로그인/refresh token 버그 수정(PR #11)** + **아키텍처 감사 fail 2건 수정 — 영속성 예외 번역(PR #12), 인증 서비스 실패 케이스 테스트(PR #13)** + **문서 디렉토리 정리(PR #15)** + **Swagger(springdoc-openapi) API 문서화(PR #16)** + **로컬 개발용 docker-compose(PR #17)** + **refresh token 저장소 MySQL → Redis 이관(PR #18)** + **문서 전체 최신화(PR #19)** + **개인화 학습 상태 조회(PR #20)** + **`Recording` 도메인(PR #21)**까지 반영된 상태(현재 `2824ad6`). `./gradlew build`(spotlessCheck 포함) BUILD SUCCESSFUL 확인됨.
  - PR #18부터 **Redis가 로그인/refresh의 필수 인프라**가 됨 — 로컬은 `docker-compose up -d`로 MySQL과 함께 기동.
  - `chore/rename-package-voicebridge`, `feature/init-project`: 각각 PR #3, PR #2 병합 완료 후 원격/로컬 브랜치 삭제 완료.
  - `fix/test-datasource-h2`: PR #4 병합 완료(`9ff44b4`), 원격/로컬 브랜치 삭제 완료.
  - `feature/auth`: `ee2d230`(feat) + `28372c6`(refactor: 주석 제거) — 인증 도메인(User) 구현 완료, **PR #5 병합 완료**(`162510b`), 원격/로컬 브랜치 삭제 완료.
  - `chore/code-quality-tooling`: `7ccdd53`(툴링) + `26a9346`(전체 재포맷) — Spotless(Google Java Format) + Jacoco 도입, **PR #6 병합 완료**(`2c01503`).
  - `feature/diagnosis-session`: `8df48bd` — 진단세션 계약(도메인/포트) + `POST /api/v1/diagnosis-sessions` 완전 구현, **PR #7 병합 완료**(`3a61fe9`). **다른 feature 브랜치와 달리 병합 후에도 삭제하지 않음** — 백엔드 A가 이 브랜치에서 나머지 4개 유스케이스(세션 조회/녹음 업로드/결과 조회/취약 음소 분석) 이어서 구현할 수 있어 사용자가 보존 요청함.
  - `feature/personalization-recognition`: `f5141e4` — 개인화/인식 계약(도메인/포트) + `GET /api/v1/personalization/model` 완전 구현. 기존에 이미 병합돼 있던 `AiInferenceClient.java`도 함께 수정됨(`ModelType`을 `domain.recognition.ModelType`으로 이동, SSOT). **PR #8 병합 완료**(`c9a747d`, 2026-09-16). **`feature/diagnosis-session`과 마찬가지로 병합 후에도 삭제하지 않음** — 태원이 이어서 작업할 수 있어 사용자가 보존 요청함.
  - `feature/ai-inference-jpyrust`: `2da6a63` — `JPyRustAiInferenceClient` 실제 구현체(JitPack `com.github.farmer0010:JPyRust:main-SNAPSHOT` 의존), 더미 WAV 왕복 통합 테스트 포함. **PR #9 병합 완료**(`5e599ca`, 2026-09-17). `feature/diagnosis-session`과 동일한 이유로 **병합 후에도 브랜치 보존**.
  - `chore/seed-sentences`: `83bfea9` — `local` 프로파일 전용 `SentenceSeeder`(멱등). **PR #10 병합 완료**(`ee498c3`). 브랜치 보존.
  - `fix/refresh-token-hash-overflow`: `15f73e6` — 로그인 500 에러 원인 2건(BCrypt 72바이트 제한, `LoginService`의 readOnly 트랜잭션에 묶인 refresh token 저장) 수정. **PR #11 병합 완료**(`eb326b0`). 브랜치 보존.
  - `fix/persistence-exception-translation`, `test/auth-service-failure-cases`: 아키텍처 감사(9번 항목 참고)에서 발견된 fail 2건 수정. **PR #12(`7d15101`), PR #13(`33412b6`) 병합 완료 후 원격/로컬 브랜치 삭제 완료**.
  - `docs/update-readme-progress`, `docs/organize-docs-directory`: README/PROGRESS.md 최신화 및 문서를 `docs/` 디렉토리로 정리. **PR #14, PR #15 병합 완료**(`d46a6e4`, `ed13256`) — 이 세션 밖에서 진행되어 상세 작업 기록은 남아있지 않음.
  - `chore/add-swagger-docs`: `f62e33b` — springdoc-openapi(Swagger UI) 도입, `OpenApiConfig` 신규 + `SecurityConfig`에 `/swagger-ui/**`·`/v3/api-docs/**` permitAll 2줄만 추가(기존 인가 규칙 변경 없음), 보안 회귀 테스트(`SwaggerSecurityTest`) 3건 포함. **PR #16 병합 완료**(`637c1da`, 2026-09-18), 원격/로컬 브랜치 삭제 완료.
  - `chore/docker-compose-local`: `a746762` — 로컬 인프라(MySQL, Redis) `docker-compose.yml` 신규(애플리케이션은 여전히 `./gradlew bootRun`). `application-local.yml`의 `DB_PASSWORD` 빈 문자열 기본값과 compose의 `MYSQL_ROOT_PASSWORD` 기본값이 어긋나 있던 걸 발견해 `MYSQL_ALLOW_EMPTY_PASSWORD` 조합으로 맞춤. **PR #17 병합 완료**(`b44fe47`, 2026-09-18), 원격/로컬 브랜치 삭제 완료.
  - `feature/redis-refresh-token`: `1a1ffc3` — refresh token 저장소를 MySQL(`RefreshTokenJpaEntity`/`RefreshTokenJpaRepository`, 둘 다 삭제)에서 Redis(`StringRedisTemplate` + 기존 `TokenHasherPort`)로 이관. `RefreshTokenStorePort` 인터페이스는 변경 없음. Testcontainers Redis 기반 통합 테스트(`RefreshTokenStoreAdapterTest`, TTL 실측 포함) 5건 신규. **PR #18 병합 완료**(`7a1f937`, 2026-09-18), 원격/로컬 브랜치 삭제 완료. **이때부터 Redis가 로그인/refresh의 필수 인프라가 됨**.
  - `feature/recording-domain`: `24e2a98` — `Recording` 도메인 + `RecordingStatus` + `RecordingRepositoryPort` 신규, 단위 테스트 10건. **develop 최신(`01a99a2`)에서 새로 딴 브랜치** — 당초 지시는 `feature/diagnosis-session`에서 이어가는 것이었으나 그 브랜치가 `8df48bd`에 멈춰 있어 PR #9~#19가 빠진 상태였음(17번 항목 참고). **PR #21 병합 완료**(`2824ad6`, 2026-09-21). 브랜치 보존.
  - 나머지 도메인 코드(취약 음소 분석 상세 로직, 인식 유스케이스가 AI 연동 어댑터를 실제로 호출하는 배선 등)는 아직 구현 전.

## 작업 이력

### 1. Git 브랜치 전략 세팅 — 완료

- `main`/`develop`은 이미 존재했고 origin과 동기화 확인.
  - Spring Initializr(Gradle) 표준 `.gitignore` 작성.
  - `CONTRIBUTING.md` 작성: `feature/<기능명>` 브랜치 규칙, 커밋 컨벤션(`feat/fix/refactor/docs/test/chore`).
  - `develop`에 커밋(`a8bbf91`) → **PR #1** (`develop` → `main`) 생성 → 병합(`2b21d4a`).

### 2. Spring Boot 초기 스캐폴딩 (헥사고날 구조) — 완료

- 전달받은 `dysarthria-backend-scaffold.zip` 검토 후 저장소 루트에 배치.
  - `domain / port(in,out) / application / adapter(in,out)` 레이어 생성, 레이어별 `package-info.java`로 책임 문서화.
  - `common/response/ApiResponse`(API 명세서 0.3절), `common/exception/{ErrorCode,CustomException,GlobalExceptionHandler}`(API 명세서 0.6절) 추가.
  - `build.gradle`: web, jpa, validation, lombok, test만 반영. 인증/Redis/S3는 YAGNI에 따라 TODO로 보류.
  - `feature/init-project` 브랜치에서 커밋(`64a2833`) → **PR #2** (`feature/init-project` → `develop`) 생성 → 병합(`4d283d3`).

### 3. 패키지 리네임 `com.gsia.dysarthria` → `com.voicebridge` — 완료

- 사전 확인: `develop` clean & 동기화, 실제 도메인 코드 없음 확인 후 진행.
  - `chore/rename-package-voicebridge` 브랜치에서 작업:
    - `git mv`로 `com/gsia/dysarthria/**` → `com/voicebridge/**` 이동(히스토리 보존).
    - 전체 `.java` 파일의 `package`/`import` 문자열 치환, diff 직접 검토.
    - `DysarthriaApplication` → `VoiceBridgeApplication`, `DysarthriaApplicationTests` → `VoiceBridgeApplicationTests`.
    - `build.gradle` group, `settings.gradle` rootProject.name, `application.yml` spring.application.name 변경.
    - `application-local.yml`의 DB 스키마명(`dysarthria`)도 사용자 확인 후 `voicebridge`로 변경.
    - 전체 grep 재확인 — 잔여 `com.gsia`/`dysarthria` 없음 (`CLAUDE.md`의 챌린지명 "GSIA SW 챌린지"는 무관하여 제외).
  - 커밋(`9d1481e`) → **PR #3** (`chore/rename-package-voicebridge` → `develop`) 생성 → 병합(`9d30137`).

### 4. gradle wrapper 정식 커밋 + 빌드 검증 — 완료

- 누군가 로컬에서 생성해둔 `gradle/`, `gradlew`, `gradlew.bat`(untracked)를 발견, `./gradlew build` 실행.
  - 컴파일/패키징(`assemble`, `bootJar`)은 성공했으나 `test`에서 실패: `@SpringBootTest`가 기본 프로파일(`local`)을 타면서 로컬에 없는 MySQL(`jdbc:mysql://localhost:3306/voicebridge`)에 연결 시도 → Connection refused → Hibernate가 Dialect를 못 정함. 패키지 리네임과는 무관한, PR #2 스캐폴딩부터 있던 기존 갭.
  - `fix/test-datasource-h2` 브랜치에서 gradle wrapper 정식 커밋(`455c740`) + `src/test/resources/application.yml` 추가해 테스트가 H2 인메모리(MySQL 모드)를 쓰도록 분리(`3d103df`). Gradle 테스트 클래스패스가 main보다 우선이라 이 파일이 `src/main/resources/application.yml`을 완전히 대체하는 방식.
  - `./gradlew build` → **BUILD SUCCESSFUL** 확인 후 **PR #4** (`fix/test-datasource-h2` → `develop`) 생성 → 병합(`9ff44b4`).

### 5. 인증 도메인 구현 (feature/auth) — 완료

- 사전 확인: `develop` clean & 동기화, `./gradlew build` BUILD SUCCESSFUL 확인 후 진행.
  - `voicebridge-auth-feature.zip` 검토 후 `feature/auth` 브랜치에 반영:
    - 전체 교체 대상(`build.gradle`, `application.yml`, `ErrorCode.java`)은 덮어쓰기 전 diff로 기존 항목 유지 여부 확인 후 진행 — 기존 의존성/에러코드 삭제 없이 추가만 됨.
    - 신규 추가: `domain/user`, `port/in`, `port/out`, `application`, `adapter/in/web`(+dto), `adapter/out/auth`, `adapter/out/persistence`, `config/SecurityConfig`.
    - 압축 임시 폴더/zip은 스크래치패드에서 작업 후 정리, 저장소에는 흔적 없음.
  - `./gradlew build` 1차 실행 시 `VoiceBridgeApplicationTests.contextLoads()` 실패: `src/test/resources/application.yml`(PR #4에서 추가, main의 application.yml을 완전히 대체)에 이번에 추가된 `voicebridge.jwt.*`/`voicebridge.kakao.*` 키가 없어서 `PlaceholderResolutionException` 발생. 인증 기능 코드 자체의 결함이 아니라 PR #4 테스트 설정 분리 방식과의 상호작용 문제 — 사용자에게 보고 후 승인받아 `src/test/resources/application.yml`에 테스트용 JWT/Kakao 기본값만 추가해서 해결.
  - `./gradlew build` 재실행 → **BUILD SUCCESSFUL**, 테스트 4개(VoiceBridgeApplicationTests, UserTest ×2, JwtTokenProviderTest) 전부 통과.
  - 보안 체크리스트 검토: `SecurityConfig`가 `/api/v1/auth/**`만 permitAll이고 나머지는 `anyRequest().authenticated()` 확인. JWT 시크릿은 `${JWT_SECRET:local-dev-secret-key-...}` 로컬 기본값 + 환경변수 오버라이드, 하드코딩된 운영 시크릿 없음. `.env`/카카오 REST API 키 등 실제 크리덴셜 전체 grep으로 없음 확인(카카오 로그인은 프론트가 카카오 SDK로 받은 accessToken을 그대로 검증하는 방식이라 백엔드가 REST API 키를 가질 필요 자체가 없는 설계).
  - 커밋(`ee2d230`) → **PR #5** (`feature/auth` → `develop`) 생성. 추가 엔드포인트: `POST /api/v1/auth/{signup,login,kakao,refresh}`, `GET /api/v1/users/me`.
  - 이후 `28372c6`(refactor: 인증 도메인 코드 주석 제거)를 `feature/auth`에 추가 반영.
  - 사용자 리뷰/승인 후 **PR #5 병합 완료**(`162510b`, 2026-09-16).

### 6. 코드 품질 툴링: Spotless + Jacoco (chore/code-quality-tooling) — 완료

- 사전 확인: `develop` clean & 동기화, `./gradlew build` BUILD SUCCESSFUL 확인 후 진행. FBRL 프로젝트 컨벤션을 3주 챌린지 상황에 맞게 축소 적용.
  - `develop`에서 `chore/code-quality-tooling` 브랜치 생성.
  - `build.gradle`에 `com.diffplug.spotless`(6.25.0), `jacoco` 플러그인 + 설정 추가:
    - `spotless.java`: `googleJavaFormat()`, `removeUnusedImports()`, `trimTrailingWhitespace()`, `endWithNewline()`.
    - `jacocoTestReport`(html/xml 리포트), `jacocoTestCoverageVerification`(최소 40%) — **의도적으로 `check`/`build`에 묶지 않음**. 3주 챌린지 초반이라 커버리지는 warn-only로 시작, 수동 실행(`./gradlew jacocoTestCoverageVerification`)만 가능. Spotless는 기본 동작대로 `spotlessCheck`가 `check`에 자동으로 엮여 포맷 위반 시 `build` 실패.
  - `./gradlew spotlessApply`로 기존 코드(스캐폴딩 + 인증 도메인) 54개 파일 일괄 재포맷 — 668 insertions(+), 656 deletions(-). 4스페이스 → GJF 2스페이스 들여쓰기, 인자 줄바꿈 등 순수 포맷팅 변경만 확인(`User.java` 등 최대 diff 파일 직접 검토).
  - 재포맷 전/후 `./gradlew test` 결과 동일 확인 — 3클래스 총 4개 테스트 전부 통과, 테스트 케이스 목록 diff 없음(로직 변경 없음 검증).
  - 저장소 루트에 `pre-commit-config.yaml` 추가(`pre-commit install` 후 커밋 시 `spotlessApply` 자동 실행). `CONTRIBUTING.md`에 pre-commit 설치법 + `spotlessCheck`/`spotlessApply`/`jacocoTestReport` 명령어 + Jacoco HTML 리포트 경로(`build/reports/jacoco/test/html/index.html`) 추가.
  - `./gradlew build`(spotlessCheck 포함) 재실행 → **BUILD SUCCESSFUL**.
  - `./gradlew jacocoTestCoverageVerification` 수동 실행 시 현재 커버리지 **19%**로 40% 기준 미달 FAILED 확인 — `build`/`check`에 안 엮여 있어 영향 없음(의도한 동작). 커버리지 개선은 별도 작업으로 남김.
  - Atomic PR 원칙에 따라 커밋 2개로 분리: `7ccdd53`(chore: Spotless/Jacoco 툴링 추가), `26a9346`(chore: Spotless 포맷팅 일괄 적용) → **PR #6** (`chore/code-quality-tooling` → `develop`) 생성 → 사용자 승인 후 병합(`2c01503`, 2026-09-16).

### 7. 진단세션 계약 + 세션 시작 유스케이스 (feature/diagnosis-session) — 완료

- 사전 확인: `develop` clean & 동기화, `./gradlew build`(spotlessCheck 포함) BUILD SUCCESSFUL 확인 후 진행.
  - `voicebridge-diagnosis-session-contract.zip` 검토 후 `develop`에서 딴 `feature/diagnosis-session` 브랜치에 반영: 기존 파일과 경로 충돌 없이 전부 **신규 파일 추가**(23개) — `domain/diagnosis`(DiagnosisSession, DiagnosisSessionStatus, Sentence), `port/in`(5개 유스케이스 인터페이스), `port/out`(AiInferenceClient, DiagnosisSessionRepositoryPort, SentenceRepositoryPort), `application/StartDiagnosisSessionService`, `adapter/in/web/DiagnosisSessionController`(+dto), `adapter/out/persistence`(DiagnosisSession·Sentence 관련 4개), 테스트 2개, 저장소 루트 `NEXT-STEPS-diagnosis-session.md`.
  - 압축 임시 폴더/zip은 스크래치패드에서 작업 후 정리, 저장소에는 흔적 없음.
  - `./gradlew build` 1차 실행 시 Spotless 포맷 위반으로 FAILED(신규 파일이 GJF 스타일이 아니었음) → `./gradlew spotlessApply`로 자동 수정(로직 변경 아니므로 별도 승인 없이 진행) → 재실행 → **BUILD SUCCESSFUL**.
  - 테스트 9개 전부 통과 확인: 기존 4개(VoiceBridgeApplicationTests, UserTest×2, JwtTokenProviderTest) + 신규 5개(DiagnosisSessionTest×3, StartDiagnosisSessionServiceTest×2).
  - 눈으로 재검토: `DiagnosisSessionController`에 `POST /api/v1/diagnosis-sessions` 하나만 매핑, 나머지 4개(세션 조회/녹음 업로드/결과 조회/취약 음소 분석)는 클래스 상단 TODO 주석으로만 존재 확인(빈 구현 없음). `AiInferenceClient` 포트에 구현체 없음(인터페이스만, PoC 대기 상태) 확인.
  - 커밋(`8df48bd`, feat: 진단세션 계약 및 세션 시작 유스케이스 구현) → **PR #7** (`feature/diagnosis-session` → `develop`) 생성. PR 본문에 완전 구현 범위(POST만), 계약만 정의된 나머지 4개(백엔드 A 담당 예정), 알려진 제약(`sentences` 시드 데이터 없어 현재 호출 시 `RESOURCE_NOT_FOUND`) 명시.
  - 사용자 승인 후 **PR #7 병합 완료**(`3a61fe9`, 2026-09-16). **`feature/diagnosis-session` 브랜치는 다른 feature 브랜치와 다르게 병합 후에도 삭제하지 않음** — 백엔드 A가 이어서 나머지 4개 유스케이스를 구현할 수 있도록 사용자 요청으로 보존.

### 8. 개인화/인식 계약 + 모델 상태 조회 유스케이스 (feature/personalization-recognition) — 완료

- 사전 확인: `develop` clean & 동기화(뒤처져 있어 pull로 최신화), `./gradlew build`(spotlessCheck 포함) BUILD SUCCESSFUL 확인 후 진행.
  - `voicebridge-personalization-recognition-contract.zip` 검토 후 `develop`에서 딴 `feature/personalization-recognition` 브랜치에 반영. 이번엔 신규 추가(20개)와 **기존 파일 수정 1건**이 섞여 있었음:
    - 신규: `domain/personalization`(PersonalizationJob, PersonalizationJobStatus), `domain/recognition`(Recognition, ModelType), `port/in`(7개 유스케이스 인터페이스), `port/out/PersonalizationJobRepositoryPort`, `application/GetPersonalizationModelService`, `adapter/in/web/PersonalizationController`(+dto), `adapter/out/persistence`(PersonalizationJob 관련 3개), 테스트 2개, `NEXT-STEPS-personalization-recognition.md`.
    - **기존 파일 수정**: `port/out/AiInferenceClient.java`(PR #7로 이미 develop에 병합돼 있던 파일) — 덮어쓰기 전 diff를 먼저 떠서 확인: 인터페이스 안에 직접 정의돼 있던 `enum ModelType`을 제거하고 신규 `domain.recognition.ModelType`(동일한 값 `BASE_ADAPTED`/`PERSONALIZED`)을 import해서 쓰도록 바뀐 것뿐(SSOT 목적), `recognize(...)` 메서드 시그니처와 `RecognitionResult` 필드는 그대로임을 확인. 기존 코드 중 `AiInferenceClient`를 실제 타입으로 참조하는 곳은 없고(주석/javadoc 언급뿐) `AiInferenceClient.ModelType` 형태로 쓴 곳도 없어 컴파일 영향 없음 확인.
  - 압축 임시 폴더/zip은 스크래치패드에서 작업 후 정리, 저장소에는 흔적 없음.
  - `./gradlew build` 1차 실행 시 Spotless 포맷 위반으로 FAILED(신규/수정 파일이 GJF 스타일이 아니었음) → `./gradlew spotlessApply`로 자동 수정(로직 변경 아니므로 별도 승인 없이 진행) → 재실행 → **BUILD SUCCESSFUL**.
  - 테스트 14개 전부 통과 확인: 기존 9개(인증 4 + 진단세션 5) + 신규 5개(PersonalizationJobTest×3, GetPersonalizationModelServiceTest×2). 작업 지시서엔 "신규 4개"로 적혀 있었으나 실제로는 3+2=5개(사소한 표기 오차, 사용자에게 보고함).
  - 눈으로 재검토: `PersonalizationController`에 `GET /api/v1/personalization/model` 하나만 매핑, 나머지(녹음 업로드/학습 트리거/학습 상태 조회)는 TODO 주석으로만 존재 확인. `RecognitionController`는 아예 존재하지 않음(구현된 유스케이스 없어 의도적으로 미생성) 확인. `AiInferenceClient` 구현체 여전히 없음(PoC 대기 상태 유지) 확인.
  - `AiInferenceClient.java` 수정이 계약 변경과 한 세트라 커밋 1개로 묶음: `f5141e4`(feat: 개인화/인식 계약 및 모델 상태 조회 유스케이스 구현, 22 files changed) → **PR #8** (`feature/personalization-recognition` → `develop`) 생성. PR 본문 최상단에 "⚠️ 기존 파일 수정 포함" 섹션을 별도로 눈에 띄게 작성해 `AiInferenceClient.java`가 신규가 아니라 기존 병합 파일 수정임을 명시.
  - 사용자 승인 후 **PR #8 병합 완료**(`c9a747d`, 2026-09-16). `feature/diagnosis-session`과 동일하게 **병합 후에도 브랜치를 삭제하지 않고 보존**(태원이 이어서 작업할 가능성 — 사용자 요청으로 보존, 삭제 여부는 추후 사용자에게 다시 확인 예정).

### 9. JPyRust 기반 AI 연동 (feature/ai-inference-jpyrust) — 완료

- 사전 확인: `develop` clean & 동기화, `./gradlew build`(spotlessCheck 포함) BUILD SUCCESSFUL 확인. JPyRust 저장소(farmer0010/JPyRust)의 PR #1(Whisper 지원)/PR #2(workDir 버그 수정) 둘 다 main 병합 완료 상태에서 시작.
  - 0단계로 JitPack에서 받은 `JPyRustBridge.java`/`ai_worker.py`를 실제로 읽어 API를 재확인(추측 금지): `processAudio(ByteBuffer data, int length, int sampleRate)`, `initialize(workDir, modelPath, confidence, memoryKey, whisperModelPath)` 5-arg 오버로드, WHISPER 응답 JSON `{"recognized_text", "confidence"}` 확정.
  - `build.gradle`에 JitPack repo + `com.github.farmer0010:JPyRust:main-SNAPSHOT` 의존성 추가 — 첫 `--refresh-dependencies` 시 GitHub 소스에서 직접 컴파일(3분 31초 소요), 성공.
  - `JPyRustAiInferenceClient` 작성: 생성자에서 `@Value` 주입값으로 `initialize(...)` 호출, `recognize()`는 오디오를 `ByteBuffer`에 담아 브릿지 호출 후 JSON을 Jackson으로 `RecognitionResult`에 매핑, 실패 시 `CustomException(AI_INFERENCE_UNAVAILABLE)`로 번역.
  - 지시된 `ByteBuffer.wrap()` 대신 `ByteBuffer.allocateDirect()` 사용 — Rust JNI(`get_direct_buffer_address`)가 direct buffer만 지원해서. 지시된 정적 `@Disabled` 대신 `Assumptions.assumeTrue()` 사용 — 모델 로드 실패 시에만 런타임 조건부로 테스트를 스킵해야 해서.
  - `application.yml`/`test/application.yml`에 `voicebridge.ai.jpyrust.instance-id`/`whisper-model-path: tiny` 추가.
  - 더미 1초 WAV 왕복 통합 테스트 추가 — 실제로 네이티브 레이어(FILE-FALLBACK WHISPER)까지 도달해 통과함을 로그로 확인(스킵 경로 아님).
  - 테스트 실행 중 YOLO 파라미터 때문에 워킹 디렉토리에 자동 생성된 `yolov8n.pt`는 커밋 대상이 아니라 삭제 + `.gitignore`에 `*.pt` 추가.
  - 커밋(`2da6a63`) → **PR #9** (`feature/ai-inference-jpyrust` → `develop`) 생성 → 사용자 승인 후 병합(`5e599ca`, 2026-09-17). `feature/diagnosis-session`과 동일한 이유로 브랜치 보존.

### 10. 로컬 개발용 sentences 시드 데이터 (chore/seed-sentences) — 완료

- 배경: `sentences` 테이블이 비어있어 `POST /api/v1/diagnosis-sessions` 호출 시 항상 `RESOURCE_NOT_FOUND` 발생 — 다음 날 회의 시연을 위해 채워야 함.
  - `SentenceSeeder`(`ApplicationRunner` + `@Profile("local")`) 작성 — `count() > 0`이면 아무것도 안 해서 재기동해도 중복 삽입 안 됨(멱등). 예시 문장 10개는 임시 시드이며 실제 문장 세트는 기획/AI팀이 확정할 것이라는 주석을 클래스 상단에 남김.
  - raw SQL(`data.sql`) 대신 애플리케이션 코드로 넣은 이유: UUID 컬럼이 MySQL/H2에서 바이너리로 매핑되는데 하드코딩 SQL 리터럴은 Hibernate의 런타임 인코딩과 어긋날 위험이 있어서.
  - 검증: Docker MySQL 8 컨테이너로 로컬(local 프로파일) 기동 → DB 직접 조회로 10개 insert 확인 → 재기동 → insert 로그 0건(멱등성 확인) → `POST /api/v1/diagnosis-sessions` 실제 호출 → `RESOURCE_NOT_FOUND` 없이 문장 목록 정상 반환(최종 성공 기준).
  - 커밋(`83bfea9`) → **PR #10** 생성 → 병합(`ee498c3`, 2026-09-17). 브랜치 보존하지 않고 이후 정리(11번 참고).

### 11. 로그인 500 에러 + refresh token 저장 버그 수정 (fix/refresh-token-hash-overflow) — 완료

- 배경: 10번 작업 중 API 검증 과정에서 `POST /api/v1/auth/login`이 항상 500을 반환하는 걸 발견, 별도 작업으로 분리해서 수정.
  - **원인 1**: `RefreshTokenStoreAdapter`가 사용자 비밀번호용 `PasswordEncoderPort`(BCrypt)를 그대로 재사용해 JWT refresh token을 해싱 — BCrypt는 입력을 72바이트로 제한하는데 JWT는 보통 그보다 길어서 `IllegalArgumentException`. → 신규 `TokenHasherPort`(SHA-256, `Sha256TokenHasherAdapter`)를 만들어 refresh token 전용으로 분리(기존 `PasswordEncoderPort`/`SignUpService`/`LoginService`의 실제 비밀번호 검증 로직은 그대로 둠).
  - **원인 2**: 원인 1을 고친 뒤에도 로그인은 성공하지만 refresh token이 DB에 실제로 저장되지 않아 `/auth/refresh`가 항상 `REFRESH_TOKEN_INVALID` — `LoginService`가 클래스 레벨 `@Transactional(readOnly = true)`였는데 내부에서 `TokenIssuer` → `RefreshTokenStoreAdapter.save()`(쓰기)까지 같은 물리 트랜잭션에 묶여 반영이 안 됨. → `@Transactional`로 변경(`KakaoLoginService`/`RefreshTokenService`는 원래 문제 없어 손대지 않음).
  - 검증: Docker MySQL로 signup → login → refresh 전체 흐름 실제 HTTP 호출로 확인, DB에서 SHA-256 해시(Base64 44자) 정상 저장 확인.
  - 커밋(`15f73e6`) → **PR #11** 생성 → 병합(`eb326b0`, 2026-09-17). 브랜치 보존.

### 12. 아키텍처 감사 (CLAUDE.md 3장/6장/9장 대조) — 완료

- `develop`(PR #11까지 반영, `eb326b0`)을 기준으로 A~E 22개 항목(헥사고날 순수성/대칭성, 예외·DTO·SSOT, 테스트 컨벤션, 포맷팅/커버리지, 보안)을 grep/빌드/git log로 직접 확인. 코드 수정 없이 감사만 수행.
  - 결과: 19 pass, 2 fail, 1 해당없음. 전체 라인 커버리지 33.5%(183/546).
  - **Fail 1 (B1)**: `DiagnosisSessionPersistenceAdapter`/`PersonalizationJobPersistenceAdapter`의 `save()`가 `UserPersistenceAdapter`와 달리 JPA 예외(`DataIntegrityViolationException`)를 번역 없이 그대로 던짐(현재 두 엔티티에 unique/FK 제약이 없어 실사용 리스크는 낮음).
  - **Fail 2 (C5)**: `LoginService`/`SignUpService`/`RefreshTokenService`/`KakaoLoginService`에 테스트가 아예 없고, `JwtTokenProviderTest`도 happy path만 존재 — 인증 도메인이 6장 컨벤션(정상+거부 케이스 모두 테스트)을 어기고 있음.
  - 체크리스트 밖 참고 사항: `SecurityConfig`의 CORS가 `allowedOriginPatterns("*")` + `allowCredentials(true)` 조합 — 운영 배포 전 재검토 권장(아직 액션 없음).

### 13. 감사 fail 수정 — 예외 번역 (fix/persistence-exception-translation) / 인증 테스트 (test/auth-service-failure-cases) — 완료

- 서로 무관한 두 수정이라 브랜치/커밋/PR을 분리해서 진행.
  - **fix/persistence-exception-translation**: `DiagnosisSessionPersistenceAdapter.save()`/`PersonalizationJobPersistenceAdapter.save()`에 `UserPersistenceAdapter`와 동일한 try-catch 패턴 추가, 특정 비즈니스 의미가 없어 `INTERNAL_SERVER_ERROR`로 번역 + 클래스 상단에 "제약 추가 시를 대비한 방어 코드" 주석. 재현 불가능한 케이스라 억지 Mock 테스트는 만들지 않음. 커밋(`c67909e`) → **PR #12** → 병합(`7d15101`, 2026-09-17).
  - **test/auth-service-failure-cases**: 기존 `StartDiagnosisSessionServiceTest` 패턴(`@ExtendWith(MockitoExtension.class)`, 명시적 생성자 주입, `assertThatThrownBy`)으로 `SignUpServiceTest`/`LoginServiceTest`/`KakaoLoginServiceTest`/`RefreshTokenServiceTest` 신규 작성(정상+실패 케이스 총 12개) + `JwtTokenProviderTest`에 만료/위조 토큰 케이스 2개 추가. 총 신규 테스트 15개 전부 통과.
    - 커버리지 개선(수정 전 → 후): `application` 30.0%(18/60) → **85.0%(51/60)**, `adapter.out.auth` 29.4%(20/68) → **35.3%(24/68)**, 전체 33.5%(183/546) → **42.1%(230/546)**.
    - 커밋(`f18297a`) → **PR #13** → 병합(`33412b6`, 2026-09-17).
  - 두 브랜치 모두 병합 후 **원격/로컬 브랜치 삭제 완료**(`feature/diagnosis-session` 등과 달리 이어서 작업할 사람이 없는 일회성 수정이라 보존 요청 없었음).

### 14. Swagger(springdoc-openapi) API 문서화 (chore/add-swagger-docs) — 완료

- 사전 확인: `develop` clean & 동기화, `./gradlew build`(spotlessCheck 포함) BUILD SUCCESSFUL 확인 후 진행.
  - `build.gradle`에 `springdoc-openapi-starter-webmvc-ui:2.8.14` 추가 — `./gradlew dependencies`로 springdoc 3.x가 딸려오지 않음을 확인(Spring Boot 3.5.8과 호환되는 마지막 2.x 라인).
  - `OpenApiConfig.java` 신규 생성 — JWT bearer 시큐리티 스킴 포함.
  - `SecurityConfig.java`는 인가 규칙에 `/swagger-ui/**`, `/v3/api-docs/**` permitAll **두 줄만** 추가 — 덮어쓰기 전 diff를 사용자에게 먼저 보여주고 적용, `/api/v1/auth/**` permitAll과 `anyRequest().authenticated()`는 그대로 유지.
  - 1차 빌드 시 Spotless 포맷 위반(신규 파일이 GJF 스타일 아님) → `spotlessApply`로 자동 정리 후 재통과.
  - 보안 관련 변경이라 6장 컨벤션(정상+우회 차단 케이스 모두 테스트)에 맞춰 `SwaggerSecurityTest`(H2 기반 `@SpringBootTest`+`MockMvc`) 3건 신규: swagger-ui/api-docs 200, 기존 보호 엔드포인트(`/api/v1/users/me`)는 여전히 403(permitAll 누수 없음).
  - 로컬에 MySQL이 없어 `bootRun` 직접 확인이 처음엔 불가했음 → 이후 Docker Desktop 기동 + 임시 MySQL 컨테이너로 실제 서버를 띄워 브라우저(Chrome, claude-in-chrome)로 Swagger UI가 정상 렌더링되는 것을 직접 확인(진단세션/인증/유저 컨트롤러 전부 노출, 보호 엔드포인트엔 자물쇠 아이콘 표시) 후 컨테이너/프로세스 정리.
  - 커밋(`f62e33b`) → **PR #16** 생성. 보안 설정 변경 포함이라 자동 병합하지 않고 대기 → 사용자 승인 후 **병합 완료**(`637c1da`, 2026-09-18).

### 15. 로컬 개발용 docker-compose 도입 (chore/docker-compose-local) — 완료

- 배경: 로컬 기동 시마다 MySQL을 수동으로 띄워야 했음(14번 작업 중에도 임시 컨테이너로 우회). 16번 작업으로 Redis도 필요해져 인프라 컨테이너를 한 번에 관리할 방법이 필요했음.
  - `docker-compose.yml` 신규 — MySQL 8.0 + Redis 7-alpine, healthcheck 포함(인프라만, 애플리케이션은 여전히 `./gradlew bootRun`).
  - 지시받은 템플릿(`MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-root}`)을 그대로 쓰지 않고 `application-local.yml`을 직접 열어 확인 — `DB_PASSWORD` 미설정 시 앱 기본값이 **빈 문자열**이라 템플릿대로면 환경변수 없이 실행 시 인증이 어긋남을 발견. `MYSQL_ALLOW_EMPTY_PASSWORD: "yes"` + `MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-}` 조합으로 앱 기본값과 정확히 맞춤.
  - `docs/CONTRIBUTING.md`에 실행법 한 줄 추가.
  - 검증: `docker compose up -d` → 두 컨테이너 healthy, `DB_PASSWORD` 미설정 상태로 `bootRun` → MySQL 연결·스키마 생성·시드 삽입 로그 확인(기본값 불일치 없음을 실증), `docker compose down` → 정상 정리 확인.
  - 커밋(`a746762`) → **PR #17** 생성, 자동 병합하지 않고 대기 → 사용자 승인 후 **병합 완료**(`b44fe47`, 2026-09-18).

### 16. Refresh token 저장소 MySQL → Redis 이관 (feature/redis-refresh-token) — 완료

- 배경: `RefreshTokenJpaEntity`/`RefreshTokenJpaRepository`/`RefreshTokenStoreAdapter`가 MySQL에 해시된 refresh token을 저장했는데 만료된 행을 정리하는 로직이 없어 계속 쌓이기만 했음. `RefreshTokenStorePort`(port.out)는 변경하지 않고 구현체만 Redis로 교체(헥사고날 원칙).
  - 사전 조사: `TokenHasherPort`/`Sha256TokenHasherAdapter`(PR #11에서 BCrypt 대신 분리해둔 것) 확인, `RefreshTokenJpaEntity`/`RefreshTokenJpaRepository`가 `RefreshTokenStoreAdapter` 외에는 어디서도 쓰이지 않음을 grep으로 확인 후 삭제.
  - `build.gradle`에 `spring-boot-starter-data-redis` + 테스트용 `testcontainers`/`testcontainers-junit-jupiter:1.20.4` 추가, `application.yml`에 `spring.data.redis.host/port`(`REDIS_HOST`/`REDIS_PORT`, 기본 `localhost:6379`) 추가.
  - `RefreshTokenStoreAdapter`를 `StringRedisTemplate` + `Duration.ofSeconds(refreshTokenExpireSeconds)` TTL 기반으로 재작성(`PasswordEncoderPort`/BCrypt로 되돌리지 않도록 주의 — PR #11에서 고친 버그).
  - 테스트 전략: Mockito 대신 **Testcontainers Redis**를 선택 — 이번 변경의 핵심(TTL이 실제로 설정되는지)은 mock으로 검증 불가능하고, 프로젝트 컨벤션(6장: 영속성 어댑터는 실제 인프라로 통합 테스트)과도 일치. `RefreshTokenStoreAdapterTest` 5건(저장 후 검증 성공/다른 토큰 실패/미저장 사용자 실패/revoke 후 실패/TTL 설정값 일치) 신규.
  - 검증: `./gradlew build` BUILD SUCCESSFUL(테스트 14클래스 전부 통과). 15번 작업의 docker-compose로 Redis 기동 후 실제 서버 구동, HTTP로 signup→login→refresh 전 구간 호출 — `redis-cli KEYS/TTL/GET`으로 키 생성·TTL(1209596초 ≈ 설정값 1209600초)·SHA-256 해시(원문 아님) 저장을 직접 확인, refresh 후 기존 토큰 재사용 시도가 `REFRESH_TOKEN_INVALID`로 차단됨(토큰 회전)도 확인.
  - PR 본문에 삭제 파일 목록과 "Redis 미기동 시 로그인 자체가 실패하는 새 운영 의존성" 명시.
  - 커밋(`1a1ffc3`) → **PR #18** 생성, 자동 병합하지 않고 대기 → 사용자 승인 후 **병합 완료**(`7a1f937`, 2026-09-18).

### 17. Recording 도메인 설계 (feature/recording-domain) — 완료

- 배경: 진단 세션 5개 엔드포인트 중 세션 시작(PR #7)만 구현된 상태인데, 나머지 4개가 전부 의존하는 `Recording` 도메인이 어디에도 없었음. 팀장이 넘긴 작업 지시(필드 구성, 팩토리 메서드, 상태 전이 메서드)를 기준으로 신규 설계.
  - **브랜치 기준 변경**: 지시문에는 `feature/diagnosis-session`에서 이어가라고 되어 있었으나, 그 브랜치는 `8df48bd`에 멈춰 있고 그 사이 develop에 PR #9~#19(JPyRust 연동, 시드 데이터, Swagger, docker-compose, Redis 이관, 문서 정리)가 들어와 있었음. 지시문 자체도 "`AiInferenceClient`는 구현체 없음"(실제로는 PR #9로 존재), "`sentences` 시드해뒀음"(PR #10, 해당 브랜치엔 없음)처럼 서로 다른 시점의 정보가 섞여 있어 사용자 확인 후 **develop 최신(`01a99a2`)에서 새 브랜치**를 따는 방식으로 진행.
  - `domain/diagnosis/Recording.java` 신규: `private` 생성자 + `create()`/`reconstitute()` 정적 팩토리, 상태 전이 `markProcessing()`/`markProcessed()`, 질의 `isOwnedBy()`/`isDone()`. setter 없음(Rich Domain Model). 변하지 않는 필드에 `final`을 붙여 변경 가능 여부를 컴파일러가 강제하게 함.
  - `domain/diagnosis/RecordingStatus.java` 신규: `UPLOADED`/`PROCESSING`/`DONE`. `PROCESSING`은 AI 인식을 비동기로 트리거하는 설계를 전제로 둔 중간 상태.
  - `port/out/RecordingRepositoryPort.java` 신규: `save`/`findById`/`findBySessionId` 3개만 정의 — 남은 4개 엔드포인트가 실제로 쓰는 것만(YAGNI).
  - `RecordingTest` 10건: 초기 상태 / 생성 검증 2건 / 정상 전이 / 가드 위반 2건 / 빈 결과 허용 / null 차단 / confidence 범위 / 소유권. Mockito 없이 순수 단위 테스트(`DiagnosisSessionTest` 패턴).
  - **리뷰에서 발견된 버그 수정**: `markProcessed()`가 `recognizedText.isBlank()`를 예외로 막고 있었는데, JPyRust는 무음·비언어 오디오에 `{"recognized_text": "", "confidence": 0.0}`를 **정상 응답**으로 돌려준다(리뷰어가 PoC 때 실측). 이대로면 해당 녹음이 `PROCESSING`에 갇히고, 취약 음소 분석은 세션의 모든 녹음이 `DONE`이어야 하므로 **세션 전체가 영구 완료 불가** 상태가 됨. 구음장애 발화 특성상 인식 실패가 흔하고 그 패턴 자체가 취약 음소 분석 데이터라 더더욱 막으면 안 되는 지점. `null`만 차단하도록 수정하고, 빈 문자열을 왜 허용하는지 주석으로 남김(선의로 되돌려지는 것을 막기 위해).
  - 리뷰 반영 2건: `confidence` 0~1 범위 검증 추가, `userId` 중복 보관 사유 주석(`DiagnosisSession`에도 있는 값이지만 결과 조회 시 세션 조회를 피하기 위한 **의도된 비정규화** — 대신 `create()` 호출 전에 세션 소유권 검증이 선행돼야 한다는 조건도 함께 명시).
  - 검증: `./gradlew test --tests "com.voicebridge.domain.diagnosis.*"` BUILD SUCCESSFUL(10건 전부 통과). 이 머신의 기본 JDK가 26이라 `spotlessJava`가 깨져 `JAVA_HOME`으로 JDK 17을 지정해 실행함 — 원인과 대응은 [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md) 참고.
  - 커밋 4개(`67ec151` feat → `137c6e9` refactor → `d5cea11` test → `24e2a98` fix) → **PR #21** 생성 → 리뷰어 승인 → 리뷰 코멘트 2건 답글 처리 → **병합 완료**(`2824ad6`, 2026-09-21). 브랜치 보존.
  - 참고: 리뷰어는 지적 2건을 남긴 뒤 **수정을 기다리지 않고 먼저 승인**했다(비차단 제안). 승인 시점이 수정 커밋보다 1시간 앞서므로, 리뷰어는 최종 코드를 보지 않은 상태에서 승인한 것 — 답글로 처리 내역을 남겨두었다.

### 18. 빌드 문자셋 UTF-8 고정 (fix/encoding) — 완료

- 배경: PR #21 리뷰에서 "테스트 메서드명을 한글로 쓴 이유가 있는지, 깨질 수도 있다"는 의견을 받아 조사한 결과, 원인이 테스트명이 아니라 `build.gradle`의 **인코딩 설정 누락**이었음.
  - Gradle은 인코딩을 지정하지 않으면 빌드하는 컴퓨터의 기본 문자셋을 따른다. 맥/리눅스는 대개 UTF-8이지만 **한국어 윈도우는 MS949**라, 소스의 한글이 깨진 채로 컴파일된다. `ErrorCode` 메시지와 도메인 예외 문구가 깨지면 그대로 API 응답에 실려 나가고, 한글 테스트 메서드명은 자바 식별자로 성립하지 않아 **빌드 자체가 실패**한다.
  - `tasks.withType(JavaCompile)`에 `options.encoding = 'UTF-8'`, 테스트에 `systemProperty 'file.encoding'` 추가(3줄).
  - 리뷰어가 "상위 자바 버전에서는 왜 이 문제가 없는지 고민해보라(키워드: OS 로케일)"는 질문을 남김 — **자바 18의 JEP 400 "UTF-8 by Default"** 때문. 17까지는 `file.encoding` 기본값이 OS 로케일에서 결정됐고, 18부터 OS와 무관하게 UTF-8로 고정됐다(OS 로케일 값은 `native.encoding`으로 분리). 즉 이 PR은 자바 18의 기본 동작을 17에서 미리 적용하는 것이고, 18 이상으로 올리면 불필요해진다.
  - **오진 정정**: 처음에는 MySQL 문자셋도 깨진 줄 알았으나, 실제 확인 결과 테이블이 `utf8mb4_0900_ai_ci`로 정상이었고 저장된 한글도 HEX 확인 결과 온전했다. 컨테이너 안 mysql CLI의 출력 문자셋 문제였을 뿐이라 `docker-compose.yml`은 손대지 않았다.
  - 커밋(`0897ce5`) → **PR #23** → **병합 완료**(`7c3e17c`).

### 19. 진단 세션 녹음 업로드 (feature/diagnosis-recording-upload) — 완료

- `POST /api/v1/diagnosis-sessions/{sessionId}/recordings`. multipart 음성을 저장하고 `PROCESSING`으로 기록한 뒤 **202 Accepted**를 즉시 반환. AI 인식은 커밋 이후 별도 스레드에서 수행.
  - **비동기 방식**: 팀장이 "별도 스레드 vs 이벤트 vs 동기" 중 판단을 위임함. **스프링 이벤트 + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`**를 선택. `@Async` 단독은 비동기 스레드가 커밋 전에 출발해 아직 저장되지 않은 녹음을 조회하는 경쟁 상태가 생길 수 있는데, `AFTER_COMMIT`은 그 가능성 자체를 없앤다. 핸들러는 self-invocation으로 `@Async`가 무시되지 않도록 별도 빈으로 분리.
  - **`RecordingStatus`에 `FAILED` 추가(스펙 변경)**: 주어진 스펙은 3개였으나, 비동기를 택하면서 AI 호출 실패를 HTTP 응답으로 알릴 수 없게 됨(이미 202를 보낸 뒤). 상태로 남겨야 결과 조회 API가 전달할 수 있다. 없으면 실패한 녹음이 `PROCESSING`에 영구히 갇힌다. 리뷰에서 승인받음 — **프론트 공유 필요**(화면 분기).
  - `StoragePort` 신규 + 어댑터 2개(`S3StorageAdapter` `@Profile("!local")` / `LocalFileStorageAdapter` `@Profile("local")`). 키 생성 규칙은 `StorageKeys`로 공통화해 프로파일이 바뀌어도 `s3Path` 형식이 달라지지 않게 함. **사용자가 보낸 파일명은 키에 쓰지 않고** 허용 목록(`wav`,`mp3`,`m4a`,`webm`,`ogg`)의 확장자만 추출 — 경로 조작 차단 테스트 포함.
  - 업로드 전 세션 소유권과 문장 소속을 검증하고, **검증 실패 시 파일을 올리지 않는다**(고아 파일 방지). 테스트로 `verify(storagePort, never()).upload(...)` 검증.
  - **`JPyRustAiInferenceClient`에 `@Profile("!local")` + 로컬 스텁 추가**(별도 커밋 `88f48ce`): 이 클라이언트가 생성자에서 파이썬 venv 생성과 `pip install`을 수행해, 환경이 없는 머신에서는 **빈 생성 실패 → 앱 전체 기동 불가**였다. 근본 원인은 JPyRust가 Python 3.13을 쓰는데 `requirements.txt`가 `numpy==1.26.4`, `torch==2.2.0` 등 3.13용 빌드가 없는 버전으로 고정돼 있어 소스 컴파일을 시도하다 `ModuleNotFoundError: No module named 'mesonpy'`로 실패하는 것. 담당 영역 밖이라 PR에 명시하고 리뷰에서 승인받음(파이썬 환경은 팀장이 직접 수정하기로).
  - 검증: 로컬 구동으로 회원가입 → 로그인 → 세션 시작 → 업로드(202) → 비동기 인식 → DB 반영 전 구간 확인. 인식이 `task-1` 스레드에서 실행됨을 로그로 확인(비동기 동작 검증).
  - 리뷰 요청으로 **비동기 핸들러 테스트 6건 추가**(`5f482ce`) — 예외를 삼키고 상태로만 남기는 구조라 테스트가 없으면 오동작이 드러나지 않음. AI 실패 → `FAILED`, 녹음 미존재 시 조기 리턴, 예외를 밖으로 던지지 않는지까지 검증.
  - **PR #24 병합 완료**(`d3378fe`).

### 20. 진단 세션 조회 / 녹음 결과 조회 (feature/diagnosis-session-queries) — 진행 중

- `GET /{sessionId}`, `GET /{sessionId}/recordings/{recordingId}/result` 두 개를 한 브랜치에 담았다(팀장 가이드가 "단순 조회"로 묶어둔 단위).
  - 세션 조회는 **문장별 녹음 상태**를 함께 반환한다. 계약의 TODO가 "문장별 녹음 여부 등"이었고, 프론트가 이어하기를 구현하려면 필요하다. 같은 문장을 다시 녹음한 경우 **가장 최근 녹음**을 노출하며, 문장 순서는 세션에 정의된 낭독 순서를 유지한다(`@OrderColumn`으로 보존됨).
  - 결과 조회는 소유권을 `Recording.userId`로 검증해 **세션을 다시 조회하지 않는다**(의도된 비정규화의 값어치). 경로의 `sessionId`와 녹음의 실제 소속이 다르면 400으로 거부한다 — 없으면 아무 세션 ID나 넣어도 통과해 URL이 의미를 잃는다.
  - `SentenceRepositoryPort`에 `findAllByIds` 추가. 두 유스케이스 모두 문장 ID로 원문을 조회해야 하는데 기존에는 무작위 N개를 뽑는 메서드만 있었다.
  - 테스트 14건(세션 조회 6 / 결과 조회 8). 전체 88건 통과.

## 알려진 이슈 / 확인 필요 사항

> 과거에 실제로 겪고 해결한 에러(빌드/테스트, 인증, AI 연동, Git/GitHub 운영 등)는 여기서 빼고 [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md)로 옮겼습니다. 아래는 아직 해결되지 않은, 열려있는 항목만 남겨둡니다.

- **`AsyncConfig`에 커스텀 Executor가 없다.** `@EnableAsync`만 선언해 둔 상태라 기본 실행기를 쓰며, 스레드 풀 크기·큐 용량·거부 정책을 제어하지 못한다. 업로드가 몰리면 스레드가 무제한으로 늘어날 수 있다. 시연 규모에서는 문제가 되지 않아 의도적으로 미루지만, 운영 전에는 `ThreadPoolTaskExecutor`를 명시하고 AI 추론 동시 실행 수를 제한해야 한다.
- **비동기 인식 중 서버가 죽으면 해당 녹음이 `PROCESSING`에 갇힌다.** 재시도 경로가 없어 세션이 완료 불가 상태가 된다(취약 음소 분석은 모든 녹음이 종료 상태여야 함). 복구 수단(재시도 API 또는 오래된 `PROCESSING` 정리 스케줄러)을 만들지는 취약 음소 분석 착수 전에 결정한다.
- **`S3StorageAdapter`는 실제로 검증하지 못했다.** 버킷과 크레덴셜이 없어 로컬 파일 어댑터로만 확인했다. 인프라 쪽에 프로비저닝 요청이 등록돼 있다.

- **`AsyncConfig`에 커스텀 Executor가 없다.** `@EnableAsync`만 선언해 둔 상태라 기본 실행기를 쓰며, 스레드 풀 크기·큐 용량·거부 정책을 제어하지 못한다. 업로드가 몰리면 스레드가 무제한으로 늘어날 수 있다. 시연 규모에서는 문제가 되지 않아 의도적으로 미루지만, 운영 전에는 `ThreadPoolTaskExecutor`를 명시하고 AI 추론 동시 실행 수를 제한해야 한다.
- **비동기 인식 중 서버가 죽으면 해당 녹음이 `PROCESSING`에 갇힌다.** 재시도 경로가 없어 세션이 완료 불가 상태가 된다(취약 음소 분석은 모든 녹음이 종료 상태여야 함). 복구 수단(재시도 API 또는 오래된 `PROCESSING` 정리 스케줄러)을 만들지는 취약 음소 분석 착수 전에 결정한다.

- 아키텍처 감사(12번)에서 발견된 남은 참고 사항: `SecurityConfig`의 CORS가 `allowedOriginPatterns("*")` + `allowCredentials(true)` 조합 — 감사 체크리스트 항목엔 없어 수정하지 않았음, 운영 배포 전 재검토 필요.
- 인식 유스케이스(녹음 업로드 → 실제 인식 → 결과 조회)가 아직 없어서 `JPyRustAiInferenceClient`(PR #9)는 인프라 배선만 완료된 상태 — API 레벨에서는 아직 아무 효과가 없음.
- 테스트 커버리지 19% → 42.1%(PR #13) → **48.6%**(PR #18 기준, `jacocoTestReport` 실측: 266/547 라인)로 계속 개선 중이지만 `adapter.in.web`/`adapter.in.web.dto`/`adapter.out.persistence`는 여전히 0% — `jacocoTestCoverageVerification`은 여전히 `build`/`check`에 묶여 있지 않음(warn-only 유지 중).
- `docker-compose.yml`(PR #17)은 **로컬 개발 전용**이다 — MySQL `MYSQL_ALLOW_EMPTY_PASSWORD` 등 프로덕션에 쓰면 안 되는 설정이 포함되어 있음. 운영 배포용 compose/매니페스트는 별도로 준비해야 한다(스코프 밖).
- PR #18부터 Redis가 로그인/refresh의 필수 인프라가 됨 — 운영 배포 시 Redis 프로비저닝을 반드시 함께 계획해야 한다(현재 운영 Redis 이중화/영속성 정책은 미정).

## 다음 단계 후보

- 백엔드 A가 진단 세션 나머지 4개 유스케이스(세션 조회/녹음 업로드/결과 조회/취약 음소 분석)를 **엔드포인트별 브랜치로 나눠** 이어서 구현. PR #21(`Recording` 도메인) 병합 후 develop에서 분기하며, 다음은 **녹음 업로드**(`UploadDiagnosisRecordingUseCase`) 차례.
  - 업로드 구현 전 선행 필요: `build.gradle`에 AWS SDK(`software.amazon.awssdk:s3`) 추가, `port/out/StoragePort` 정의(`String upload(byte[] fileBytes, String fileName)`), `adapter/out/storage` 구현체 — 현재 이 패키지는 `package-info.java`만 있는 빈 상태.
  - 미결정 사항 2건: (1) AI 인식 비동기 트리거 방식(동기 / `@Async` / 스프링 이벤트 + `@TransactionalEventListener`) — 팀장이 판단을 위임했고 코드리뷰에서 함께 논의 예정. 비동기 선택 시 서버 재시작으로 `PROCESSING`에 갇힌 녹음을 되살릴 복구 수단을 함께 설계할지 결정 필요. (2) 로컬 개발에서 S3를 어떻게 대체할지(실제 버킷 / LocalStack / 파일시스템 어댑터를 프로파일로 분리) — 인프라 담당 확인 필요.
- `feature/diagnosis-session` 브랜치(보존 중)는 `8df48bd`에서 멈춰 있고 develop과 크게 벌어져 있음 — 더 쓰지 않을 거면 정리 여부를 팀에서 결정할 것.
- 백엔드 B(태원으로 추정, 팀 R&R 미확정이라 단정은 보류)가 `feature/personalization-recognition` 브랜치(보존 중)에서 나머지 유스케이스(녹음 업로드/학습 트리거/학습 상태 조회/실사용 인식/인식 이력) 이어서 구현 — `JPyRustAiInferenceClient`(`feature/ai-inference-jpyrust` 브랜치, 보존 중)를 실제로 호출하는 배선이 핵심.
- 컨트롤러/DTO/영속성 어댑터 계층 통합 테스트 보강(현재 0%) — 도메인/application 계층은 이미 양호한 수준.
- `SecurityConfig` CORS 설정(`allowedOriginPatterns("*")` + `allowCredentials(true)`) 운영 배포 전 재검토.
- 운영 배포용 인프라 정의(docker-compose는 로컬 전용) — 특히 Redis(PR #18부터 필수 의존성) 프로비저닝 계획 수립.
- 스캐폴딩 + 주요 feature 안정화 후 `develop` → `main` 승격 PR.
