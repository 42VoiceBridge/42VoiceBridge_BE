# 작업 진행 기록

> 매 작업이 끝날 때마다 이 문서를 갱신한다. 새 대화를 시작할 때 이 문서부터 읽으면 이전 지시사항이 어떻게 끝났는지 복사-붙여넣기 없이 파악할 수 있다.

마지막 업데이트: 2026-09-16

## 지금 상태 요약

- `main`: `2b21d4a` — `.gitignore`, `CONTRIBUTING.md`만 반영된 상태. 실제 코드 없음.
  - `develop`: `56f9348` — 헥사고날 스캐폴딩 + 패키지 `com.voicebridge` 리네임 + gradle wrapper + 테스트 H2 데이터소스 분리 + 인증 도메인(PR #5) + Spotless/Jacoco 코드 품질 툴링(PR #6) + 진단세션 계약(PR #7)까지 반영된 상태. `./gradlew build`(spotlessCheck 포함) BUILD SUCCESSFUL, 테스트 9개 전부 통과 확인됨.
  - `chore/rename-package-voicebridge`, `feature/init-project`: 각각 PR #3, PR #2 병합 완료 후 원격/로컬 브랜치 삭제 완료.
  - `fix/test-datasource-h2`: PR #4 병합 완료(`9ff44b4`), 원격/로컬 브랜치 삭제 완료.
  - `feature/auth`: `ee2d230`(feat) + `28372c6`(refactor: 주석 제거) — 인증 도메인(User) 구현 완료, **PR #5 병합 완료**(`162510b`), 원격/로컬 브랜치 삭제 완료.
  - `chore/code-quality-tooling`: `7ccdd53`(툴링) + `26a9346`(전체 재포맷) — Spotless(Google Java Format) + Jacoco 도입, **PR #6 병합 완료**(`2c01503`).
  - `feature/diagnosis-session`: `8df48bd` — 진단세션 계약(도메인/포트) + `POST /api/v1/diagnosis-sessions` 완전 구현, **PR #7 병합 완료**(`3a61fe9`). **다른 feature 브랜치와 달리 병합 후에도 삭제하지 않음** — 백엔드 A가 이 브랜치에서 나머지 4개 유스케이스(세션 조회/녹음 업로드/결과 조회/취약 음소 분석) 이어서 구현할 수 있어 사용자가 보존 요청함.
  - `feature/personalization-recognition`: `f5141e4` — 개인화/인식 계약(도메인/포트) + `GET /api/v1/personalization/model` 완전 구현. 기존에 이미 병합돼 있던 `AiInferenceClient.java`도 함께 수정됨(아래 8번 참고). **PR #8 오픈, 병합 대기 중** (사용자 확인 후 병합 예정 — 자동 병합하지 않음). 이 브랜치도 병합 후 삭제하지 않고 보존 예정(태원이 이어서 작업할 수 있음).
  - 나머지 도메인 코드(취약 음소 분석 상세 로직, AI 연동 실제 구현 등)는 아직 구현 전.

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

### 8. 개인화/인식 계약 + 모델 상태 조회 유스케이스 (feature/personalization-recognition) — PR 생성, 병합 대기 중

- 사전 확인: `develop` clean & 동기화(뒤처져 있어 pull로 최신화), `./gradlew build`(spotlessCheck 포함) BUILD SUCCESSFUL 확인 후 진행.
  - `voicebridge-personalization-recognition-contract.zip` 검토 후 `develop`에서 딴 `feature/personalization-recognition` 브랜치에 반영. 이번엔 신규 추가(20개)와 **기존 파일 수정 1건**이 섞여 있었음:
    - 신규: `domain/personalization`(PersonalizationJob, PersonalizationJobStatus), `domain/recognition`(Recognition, ModelType), `port/in`(7개 유스케이스 인터페이스), `port/out/PersonalizationJobRepositoryPort`, `application/GetPersonalizationModelService`, `adapter/in/web/PersonalizationController`(+dto), `adapter/out/persistence`(PersonalizationJob 관련 3개), 테스트 2개, `NEXT-STEPS-personalization-recognition.md`.
    - **기존 파일 수정**: `port/out/AiInferenceClient.java`(PR #7로 이미 develop에 병합돼 있던 파일) — 덮어쓰기 전 diff를 먼저 떠서 확인: 인터페이스 안에 직접 정의돼 있던 `enum ModelType`을 제거하고 신규 `domain.recognition.ModelType`(동일한 값 `BASE_ADAPTED`/`PERSONALIZED`)을 import해서 쓰도록 바뀐 것뿐(SSOT 목적), `recognize(...)` 메서드 시그니처와 `RecognitionResult` 필드는 그대로임을 확인. 기존 코드 중 `AiInferenceClient`를 실제 타입으로 참조하는 곳은 없고(주석/javadoc 언급뿐) `AiInferenceClient.ModelType` 형태로 쓴 곳도 없어 컴파일 영향 없음 확인.
  - 압축 임시 폴더/zip은 스크래치패드에서 작업 후 정리, 저장소에는 흔적 없음.
  - `./gradlew build` 1차 실행 시 Spotless 포맷 위반으로 FAILED(신규/수정 파일이 GJF 스타일이 아니었음) → `./gradlew spotlessApply`로 자동 수정(로직 변경 아니므로 별도 승인 없이 진행) → 재실행 → **BUILD SUCCESSFUL**.
  - 테스트 14개 전부 통과 확인: 기존 9개(인증 4 + 진단세션 5) + 신규 5개(PersonalizationJobTest×3, GetPersonalizationModelServiceTest×2). 작업 지시서엔 "신규 4개"로 적혀 있었으나 실제로는 3+2=5개(사소한 표기 오차, 사용자에게 보고함).
  - 눈으로 재검토: `PersonalizationController`에 `GET /api/v1/personalization/model` 하나만 매핑, 나머지(녹음 업로드/학습 트리거/학습 상태 조회)는 TODO 주석으로만 존재 확인. `RecognitionController`는 아예 존재하지 않음(구현된 유스케이스 없어 의도적으로 미생성) 확인. `AiInferenceClient` 구현체 여전히 없음(PoC 대기 상태 유지) 확인.
  - `AiInferenceClient.java` 수정이 계약 변경과 한 세트라 커밋 1개로 묶음: `f5141e4`(feat: 개인화/인식 계약 및 모델 상태 조회 유스케이스 구현, 22 files changed) → **PR #8** (`feature/personalization-recognition` → `develop`) 생성. PR 본문 최상단에 "⚠️ 기존 파일 수정 포함" 섹션을 별도로 눈에 띄게 작성해 `AiInferenceClient.java`가 신규가 아니라 기존 병합 파일 수정임을 명시.
  - **자동 병합하지 않음 — 사용자 확인/승인 대기 중.** 승인 시 `feature/diagnosis-session`과 동일하게 병합 후에도 브랜치를 삭제하지 않고 사용자에게 먼저 물어볼 예정(태원이 이어서 작업할 가능성).

## 알려진 이슈 / 확인 필요 사항

- `dysarthria-backend-scaffold.zip`이 저장소 루트에 남아있음(git 미포함) — 필요 없으면 수동 삭제 가능.
- GitHub PR 병합 시 `gh pr merge`(GraphQL) 및 REST `gh api PUT .../merge` 둘 다 간헐적으로 502 또는 "Merge already in progress" 405를 반복 반환하는 경우가 있었음(PR #3, #4에서 재현, 길게는 수 분간 지속). 원인은 확실치 않지만, 관찰된 패턴상 merge 요청이 GitHub 서버에는 이미 접수되어 비동기로 처리 중인데 그 처리(브랜치 보호 규칙 평가, 백그라운드 머지 작업 큐)가 지연되는 것으로 보임 — 즉 요청이 실패한 게 아니라 아직 끝나지 않은 상태. 대응: `gh pr view --json mergedAt`으로 실제 상태를 먼저 확인하고, 병합 전이면 15초 간격 재시도 루프(`while true` — `until true`로 쓰면 즉시 종료되므로 주의)로 처리. `gh pr view`가 일시적으로 빈 문자열을 반환할 수 있으니 병합 여부 판단 시 빈 문자열과 `null`을 반드시 구분해서 체크할 것.
- `gh pr review --approve`는 PR 작성자와 병합 실행 계정이 같으면(`gh` 인증 계정 = PR author) GitHub이 자체 승인(self-approve)을 막아 실패함(`Can not approve your own pull request`, PR #6·#7에서 재현). 사용자가 채팅상으로 승인 의사를 밝히면 별도 GitHub 리뷰 승인 없이 병합만 진행하는 방식으로 대응 중.
- `sentences` 테이블에 시드 데이터가 없어 `POST /api/v1/diagnosis-sessions`를 지금 호출하면 `RESOURCE_NOT_FOUND` 발생(PR #7). 코드 결함 아님, 시드 데이터 작업 필요.
- PR #8: `AiInferenceClient.java` 수정이 섞인 PR이라 리뷰 시 "이거 신규 아니네?" 하고 헷갈릴 수 있음 — PR 본문 최상단에 경고 섹션으로 표시해둠.

## 다음 단계 후보

- **PR #8(개인화/인식 계약) 사용자 확인 후 병합.**
- 백엔드 A가 `feature/diagnosis-session` 브랜치(삭제 안 하고 보존 중)에서 나머지 4개 유스케이스(세션 조회/녹음 업로드/결과 조회/취약 음소 분석) 이어서 구현.
- 백엔드 B(태원으로 추정, 팀 R&R 미확정이라 단정은 보류)가 `feature/personalization-recognition` 브랜치(PR #8 병합 후에도 보존 예정)에서 나머지 유스케이스(녹음 업로드/학습 트리거/학습 상태 조회/실사용 인식/인식 이력) 이어서 구현.
- `sentences` 테이블 시드 데이터 준비 — 없으면 진단 세션 시작 API가 항상 `RESOURCE_NOT_FOUND`를 반환함.
- AI 연동(`AiInferenceClient` 구현체) 착수 — PoC(JPyRust vs FastAPI) 결과 대기 중.
- 테스트 커버리지 19% → 40% 이상으로 끌어올리기(도메인/애플리케이션 계층 단위 테스트 보강). 기준 달성 후 `jacocoTestCoverageVerification`을 `check`에 묶을지 팀 논의.
- 스캐폴딩 + 주요 feature 안정화 후 `develop` → `main` 승격 PR.
