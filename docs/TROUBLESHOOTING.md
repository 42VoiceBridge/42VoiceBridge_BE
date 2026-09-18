# 트러블슈팅

이 프로젝트를 진행하며 실제로 겪은 에러와 해결 과정을 증상 → 원인 → 해결 순서로 기록합니다. 같은 에러를 만나면 새로 원인을 추적하기 전에 여기부터 확인하세요. 작업 흐름 전체 맥락(어떤 순서로 무엇을 했는지)은 [`PROGRESS.md`](./PROGRESS.md)를 참고하세요.

## 빌드 / 테스트

### `@SpringBootTest`가 로컬 MySQL에 연결을 시도하며 실패한다

- **증상**: `./gradlew test` 실행 시 `Connection refused`로 `jdbc:mysql://localhost:3306/voicebridge` 연결 실패 → Hibernate가 Dialect를 못 정해 컨텍스트 로딩 실패.
- **원인**: `src/main/resources/application.yml`의 기본 프로파일(`local`)이 테스트에도 그대로 적용되어, 로컬에 MySQL이 없는 CI/신규 개발 환경에서는 항상 실패.
- **해결**: `src/test/resources/application.yml`을 별도로 추가해 H2 인메모리(MySQL 모드)를 쓰도록 분리. Gradle 테스트 클래스패스가 main보다 우선이라 이 파일이 main의 `application.yml`을 완전히 대체하는 방식으로 동작한다.
- **관련**: PR #4

### 새 설정 키를 추가했는데 `PlaceholderResolutionException`으로 컨텍스트 로딩 실패

- **증상**: `application.yml`에 새 설정(예: `voicebridge.jwt.*`, `voicebridge.kakao.*`)을 추가한 뒤 테스트를 돌리면 플레이스홀더를 못 찾는다는 에러로 실패.
- **원인**: `src/test/resources/application.yml`이 main의 `application.yml`을 **완전히 대체**하는 구조라서(위 항목 참고), main에만 새 키를 추가하면 테스트 쪽에는 해당 키 자체가 없어서 발생. 새 기능 코드의 결함이 아니라 테스트 설정 분리 방식과의 상호작용 문제.
- **해결**: 새 설정 키를 추가할 때는 `src/main/resources/application.yml`과 `src/test/resources/application.yml` 양쪽에 동시에(테스트 쪽은 테스트용 값으로) 추가한다.
- **관련**: PR #5

### Spotless 포맷 위반으로 빌드가 실패한다

- **증상**: `./gradlew build`가 `spotlessJavaCheck`에서 실패, diff 형태로 포맷 위반 지점을 알려줌(주로 줄바꿈/들여쓰기).
- **원인**: 이 프로젝트는 Google Java Format(GJF) 기준으로 Spotless가 `check`에 자동으로 엮여 있어, 포맷이 안 맞으면 로직에 문제가 없어도 빌드가 실패한다.
- **해결**: `./gradlew spotlessApply`로 자동 재포맷 후 다시 빌드. 커밋 시 자동으로 돌리고 싶으면 `pre-commit install`([`CONTRIBUTING.md`](./CONTRIBUTING.md) 참고).

## 인증 / 토큰

### 로그인 호출 시 500 (`IllegalArgumentException: password cannot be more than 72 bytes`)

- **증상**: `POST /api/v1/auth/login`이 200 대신 500(`INTERNAL_SERVER_ERROR`)을 반환.
- **원인**: `RefreshTokenStoreAdapter`가 사용자 비밀번호용 `PasswordEncoderPort`(BCrypt 구현)를 그대로 재사용해 JWT refresh token을 해싱하고 있었음. BCrypt는 입력을 72바이트로 제한하는데 JWT는 보통 그보다 길다.
- **해결**: refresh token은 이미 고엔트로피 랜덤 문자열이라 BCrypt 같은 느린 솔트 해싱이 필요 없다. 사용자 비밀번호와는 별개 관심사이므로, `PasswordEncoderPort`와 같은 패턴으로 `TokenHasherPort`(SHA-256 기반, `Sha256TokenHasherAdapter`)를 새로 두고 `RefreshTokenStoreAdapter`가 이걸 쓰도록 분리했다.
- **관련**: PR #11

### 로그인은 성공하는데 `/auth/refresh` 호출 시 항상 `REFRESH_TOKEN_INVALID`

- **증상**: 로그인 응답(200)으로 refresh token은 정상 발급되는데, 그 토큰으로 곧바로 `/auth/refresh`를 호출하면 항상 유효하지 않다는 에러가 난다. DB의 `refresh_tokens` 테이블을 직접 조회하면 row 자체가 없다.
- **원인**: `LoginService`가 클래스 레벨 `@Transactional(readOnly = true)`였는데, `login()` 내부에서 `TokenIssuer` → `RefreshTokenStoreAdapter.save()`(쓰기)까지 같은 물리 트랜잭션 안에서 호출됨 — Spring의 기본 전파(REQUIRED)로 인해 이 쓰기가 readOnly 트랜잭션에 묶여 반영되지 않는다.
- **해결**: `LoginService`는 로그인이라는 이름과 달리 refresh token 저장이라는 부수효과(쓰기)를 갖고 있으므로 `readOnly = true`가 애초에 잘못된 설정이었다. `@Transactional`(쓰기 가능)로 변경. `KakaoLoginService`(이미 `@Transactional`)와 `RefreshTokenService`(클래스 레벨 어노테이션 없음)는 같은 문제가 없어 손대지 않았다.
- **관련**: PR #11

## AI 연동 (JPyRust)

### JNI 네이티브 호출에서 버퍼 주소를 읽지 못한다

- **증상**: `JPyRustBridge.processAudio(...)` 호출이 실패하거나 네이티브 레이어에서 데이터를 제대로 못 받는다.
- **원인**: Rust 쪽 JNI 구현이 `env.get_direct_buffer_address()`로 버퍼 주소를 직접 읽는데, `ByteBuffer.wrap(byte[])`으로 만든 버퍼는 JVM heap에 있는 non-direct 버퍼라 이 호출에서 주소를 얻지 못한다.
- **해결**: 반드시 `ByteBuffer.allocateDirect(length)`로 direct 버퍼를 만들어 데이터를 채운 뒤 넘겨야 한다.
- **관련**: PR #9

## 데이터 / API

### `POST /api/v1/diagnosis-sessions` 호출 시 항상 `RESOURCE_NOT_FOUND`

- **증상**: 진단 세션 시작 API를 호출하면 "낭독할 문장이 아직 준비되지 않았습니다" 에러가 항상 발생.
- **원인**: `sentences` 테이블에 시드 데이터가 하나도 없음. 코드 결함이 아니라 데이터 부재.
- **해결**: `local` 프로파일에서만 동작하는 `SentenceSeeder`(`ApplicationRunner`)를 추가해 앱 기동 시 테이블이 비어있으면 예시 문장 10개를 자동으로 채우도록 했다(재기동해도 중복 삽입 안 되게 멱등 처리). 운영(`prod`) 프로파일에서는 실행되지 않는다 — 운영 시드 데이터는 별도로 준비해야 한다.
- **관련**: PR #10

## 로컬 인프라 (docker-compose)

### `docker-compose up -d`로 MySQL을 띄웠는데 앱 기동 시 접속 인증에 실패한다

- **증상**: MySQL 공식 이미지가 `MYSQL_ROOT_PASSWORD`를 못 찾아 컨테이너 자체가 부팅을 거부하거나, 컨테이너는 떴는데 앱이 `Access denied for user 'root'`로 실패한다.
- **원인**: `application-local.yml`은 `DB_PASSWORD` 환경변수를 안 주면 기본값이 **빈 문자열**인데, `docker-compose.yml`의 `MYSQL_ROOT_PASSWORD`가 빈 값이면 MySQL 공식 이미지는 `MYSQL_ALLOW_EMPTY_PASSWORD` 없이는 부팅 자체를 거부한다. 앱과 compose의 "환경변수 미설정 시 기본 동작"이 서로 어긋나면 이런 문제가 생긴다.
- **해결**: `docker-compose.yml`에 `MYSQL_ALLOW_EMPTY_PASSWORD: "yes"`와 `MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-}`를 같이 둔다. `DB_PASSWORD`를 안 주면 앱과 동일하게 빈 비밀번호로 뜨고, 값을 주면 그 값이 root 비밀번호로 쓰인다.
- **관련**: PR #17

### 로그인/`/auth/refresh` 호출 시 Redis 연결 에러 (`RedisConnectionFailureException` 등)로 실패한다

- **증상**: MySQL은 정상인데 로그인이나 refresh 호출이 실패하며 로그에 Redis 연결 관련 예외가 찍힌다.
- **원인**: PR #18부터 refresh token 저장소가 MySQL에서 Redis로 이관되어, **Redis가 로그인 흐름의 필수 의존성**이 됐다. `docker-compose up -d`를 안 했거나 Redis 컨테이너만 내려간 상태에서 앱을 띄우면 발생한다.
- **해결**: `docker-compose up -d`로 Redis까지 함께 떠 있는지 확인한다(`docker ps`에 `redis` 컨테이너가 `healthy`인지 확인). Testcontainers를 쓰는 `./gradlew test`는 Docker 데몬만 켜져 있으면 별도로 Redis를 안 띄워도 된다.
- **관련**: PR #18

## Git / GitHub 운영

### PR 병합 요청이 502 또는 "Merge already in progress" 405를 반복 반환한다

- **증상**: `gh pr merge`(GraphQL)나 REST `gh api PUT .../merge` 호출이 간헐적으로 실패하며, 길게는 수 분간 같은 에러가 반복된다.
- **원인**: 확실치 않으나, 관찰된 패턴상 병합 요청 자체는 GitHub 서버에 이미 접수되어 비동기로 처리 중인데 그 처리(브랜치 보호 규칙 평가, 백그라운드 머지 작업 큐)가 지연되는 것으로 보인다 — 즉 요청이 실패한 게 아니라 아직 끝나지 않은 상태일 수 있다.
- **해결**: `gh pr view --json mergedAt`으로 실제 병합 상태를 먼저 확인한다. 아직 병합 전이면 15초 간격 재시도 루프로 대응한다(`while true` 사용 — `until true`는 즉시 종료되므로 주의). `gh pr view`가 일시적으로 빈 문자열을 반환할 수 있으니, 병합 여부 판단 시 빈 문자열과 `null`을 반드시 구분해서 체크한다.

### `gh pr review --approve`가 "Can not approve your own pull request"로 실패한다

- **증상**: PR을 정식으로 승인하려고 하면 GitHub API가 거부한다.
- **원인**: PR 작성자와 `gh` 인증 계정(병합을 실행하는 계정)이 동일하면 GitHub이 자체 승인(self-approve)을 막는다.
- **해결**: 팀 내부적으로 채팅 등을 통해 승인 의사가 확인됐다면, 별도의 GitHub 리뷰 승인 없이 병합만 진행한다.
