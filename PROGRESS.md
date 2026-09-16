# 작업 진행 기록

> 매 작업이 끝날 때마다 이 문서를 갱신한다. 새 대화를 시작할 때 이 문서부터 읽으면 이전 지시사항이 어떻게 끝났는지 복사-붙여넣기 없이 파악할 수 있다.

마지막 업데이트: 2026-09-16

## 지금 상태 요약

- `main`: `2b21d4a` — `.gitignore`, `CONTRIBUTING.md`만 반영된 상태. 실제 코드 없음.
  - `develop`: `2c01503` — 헥사고날 스캐폴딩 + 패키지 `com.voicebridge` 리네임 + gradle wrapper + 테스트 H2 데이터소스 분리 + 인증 도메인(PR #5) + **Spotless/Jacoco 코드 품질 툴링(PR #6) 병합**까지 반영된 상태. `./gradlew build`(spotlessCheck 포함) BUILD SUCCESSFUL 확인됨.
  - `chore/rename-package-voicebridge`, `feature/init-project`: 각각 PR #3, PR #2 병합 완료 후 원격/로컬 브랜치 삭제 완료.
  - `fix/test-datasource-h2`: PR #4 병합 완료(`9ff44b4`), 원격/로컬 브랜치 삭제 완료.
  - `feature/auth`: `ee2d230`(feat) + `28372c6`(refactor: 주석 제거) — 인증 도메인(User) 구현 완료, **PR #5 병합 완료**(`162510b`).
  - `chore/code-quality-tooling`: `7ccdd53`(툴링) + `26a9346`(전체 재포맷) — Spotless(Google Java Format) + Jacoco 도입, **PR #6 병합 완료**(`2c01503`).
  - `DiagnosisSession` 등 나머지 도메인 코드는 아직 어느 브랜치에도 없음.

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

## 알려진 이슈 / 확인 필요 사항

- `dysarthria-backend-scaffold.zip`이 저장소 루트에 남아있음(git 미포함) — 필요 없으면 수동 삭제 가능.
- GitHub PR 병합 시 `gh pr merge`(GraphQL) 및 REST `gh api PUT .../merge` 둘 다 간헐적으로 502 또는 "Merge already in progress" 405를 반복 반환하는 경우가 있었음(PR #3, #4에서 재현, 길게는 수 분간 지속). 원인은 확실치 않지만, 관찰된 패턴상 merge 요청이 GitHub 서버에는 이미 접수되어 비동기로 처리 중인데 그 처리(브랜치 보호 규칙 평가, 백그라운드 머지 작업 큐)가 지연되는 것으로 보임 — 즉 요청이 실패한 게 아니라 아직 끝나지 않은 상태. 대응: `gh pr view --json mergedAt`으로 실제 상태를 먼저 확인하고, 병합 전이면 15초 간격 재시도 루프(`while true` — `until true`로 쓰면 즉시 종료되므로 주의)로 처리. `gh pr view`가 일시적으로 빈 문자열을 반환할 수 있으니 병합 여부 판단 시 빈 문자열과 `null`을 반드시 구분해서 체크할 것.

## 다음 단계 후보

- 백엔드 A/B가 `feature/diagnosis-session` 등 나머지 도메인을 `develop`에서 분기해 구현 착수.
- 테스트 커버리지 19% → 40% 이상으로 끌어올리기(도메인/애플리케이션 계층 단위 테스트 보강). 기준 달성 후 `jacocoTestCoverageVerification`을 `check`에 묶을지 팀 논의.
- 스캐폴딩 + 주요 feature 안정화 후 `develop` → `main` 승격 PR.
