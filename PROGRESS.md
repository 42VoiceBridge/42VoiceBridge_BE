# 작업 진행 기록

> 매 작업이 끝날 때마다 이 문서를 갱신한다. 새 대화를 시작할 때 이 문서부터 읽으면 이전 지시사항이 어떻게 끝났는지 복사-붙여넣기 없이 파악할 수 있다.

마지막 업데이트: 2026-09-16

## 지금 상태 요약

- `main`: `2b21d4a` — `.gitignore`, `CONTRIBUTING.md`만 반영된 상태. 실제 코드 없음.
  - `develop`: `9ff44b4` — 헥사고날 스캐폴딩 + 패키지 `com.voicebridge` 리네임 + gradle wrapper + 테스트 H2 데이터소스 분리까지 반영된 상태. `./gradlew build` BUILD SUCCESSFUL 확인됨.
  - `chore/rename-package-voicebridge`, `feature/init-project`: 각각 PR #3, PR #2 병합 완료 후 원격/로컬 브랜치 삭제 완료.
  - `fix/test-datasource-h2`: PR #4 병합 완료(`9ff44b4`), 원격/로컬 브랜치 삭제 완료.
  - 아직 실제 도메인 코드(User, DiagnosisSession 등)는 어느 브랜치에도 없음.

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

## 알려진 이슈 / 확인 필요 사항

- `dysarthria-backend-scaffold.zip`이 저장소 루트에 남아있음(git 미포함) — 필요 없으면 수동 삭제 가능.
- GitHub PR 병합 시 `gh pr merge`(GraphQL) 및 REST `gh api PUT .../merge` 둘 다 간헐적으로 502 또는 "Merge already in progress" 405를 반복 반환하는 경우가 있었음(PR #3, #4에서 재현, 길게는 수 분간 지속). 원인은 확실치 않지만, 관찰된 패턴상 merge 요청이 GitHub 서버에는 이미 접수되어 비동기로 처리 중인데 그 처리(브랜치 보호 규칙 평가, 백그라운드 머지 작업 큐)가 지연되는 것으로 보임 — 즉 요청이 실패한 게 아니라 아직 끝나지 않은 상태. 대응: `gh pr view --json mergedAt`으로 실제 상태를 먼저 확인하고, 병합 전이면 15초 간격 재시도 루프(`while true` — `until true`로 쓰면 즉시 종료되므로 주의)로 처리. `gh pr view`가 일시적으로 빈 문자열을 반환할 수 있으니 병합 여부 판단 시 빈 문자열과 `null`을 반드시 구분해서 체크할 것.

## 다음 단계 후보

- 백엔드 A/B가 `feature/auth`, `feature/diagnosis-session` 등을 `develop`에서 분기해 도메인 엔티티 구현 착수.
- 스캐폴딩 + 주요 feature 안정화 후 `develop` → `main` 승격 PR.
