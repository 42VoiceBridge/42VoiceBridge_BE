# 42VoiceBridge_BE 프로젝트 메모리

사용자가 2026-09-18에 지정한 프로젝트 개발 원칙이다. 이 저장소의 설계·구현·리뷰와 학습 안내에 적용한다.

## 사용자와 작업 맥락

- 사용자는 백엔드 개발자 B 역할로 AI 연동, 개인화 학습 job, 실사용 인식을 담당한다.
- Java, Spring Boot, 헥사고날 아키텍처를 학습 중이므로 프로젝트 코드와 연결해 개념부터 설명한다.

## 1. 아키텍처 원칙

### Backend

- Java 17+, Spring Boot 3.x, Gradle.
- 헥사고날 아키텍처 필수: `domain/`은 순수 로직, `port/in`은 유스케이스 인터페이스, `port/out`은 영속성·외부 시스템 인터페이스, `application/`은 유스케이스 구현, `adapter/in/web`은 Controller, `adapter/out/persistence|storage|ai`는 외부 기술 구현을 담당한다.
- 생성자 주입(`@RequiredArgsConstructor`) 사용. 필드 주입 금지.
- DTO 필수. Entity 직접 반환 금지.
- `@RestControllerAdvice`와 `CustomException`으로 예외 처리를 표준화한다.
- JPA N+1에 주의하고 fetch join / `@EntityGraph`를 고려한다.

### 설계 원칙

- **DI / Composition Root**: 하위 계층이 환경설정이나 프레임워크에 직접 의존하지 않게 한다. 필요한 값은 생성자로 주입한다. 예를 들어 Adapter에서 `System.getenv()`를 직접 호출하거나 버킷명을 하드코딩하지 않고 설정 클래스에서 주입한다.
- **헥사고날 대칭성**: Outbound Port(`port/out`)뿐 아니라 Inbound Port(`port/in`, 유스케이스 인터페이스)도 정의한다. 특정 진입 방식에 대한 결합을 방지한다.
- **SSOT**: 기본값·검증 규칙 등 비즈니스 정책은 domain을 단일 기준으로 삼는다. DTO에 같은 기본값과 정책을 중복 정의하지 않는다.
- **도메인 전용 예외 + 인프라 예외 번역**: 도메인 검증 실패는 `CustomException`으로 표현한다. Adapter에서 발생한 프레임워크 예외(`DataIntegrityViolationException` 등)를 Application으로 그대로 전달하지 않고 도메인 예외로 번역한다.
- **Rich Domain Model**: 상태 변경 규칙은 도메인 엔티티가 담당한다. Service는 `recording.markProcessed(result)`와 같은 도메인 메서드를 조합한다.
- **YAGNI / 섣부른 추상화 금지**: 3주 챌린지 범위에 집중한다. 미사용 옵션, v1/v2 분기, 미래를 위한 공통화를 추가하지 않는다. Create와 Update 유스케이스도 성급히 공통화하지 않는다.
- **DB 설계**: 인덱스는 실제 조회 패턴을 기준으로 설계한다. 가능하면 애플리케이션 필터링 대신 DB 쿼리 조건을 사용한다. UUID는 앞부분만 자르지 않고 전체를 사용한다.
- **DTO 기반 API 계약**: Map이나 Dictionary를 그대로 반환하지 않는다. Response DTO로 응답 계약을 명확히 정의한다.

### JPA / Spring 안티패턴

- **AOP self-invocation**: 같은 클래스 내부에서 `this.xxx()`로 `@Transactional` 메서드를 호출해 프록시를 우회하는 패턴을 제안하지 않는다. `REQUIRES_NEW`가 필요한 로직은 별도 스프링 빈으로 분리한다.
- **테스트 격리**: 사용자는 동시성 테스트 등의 데이터 초기화에 `deleteAll()` 대신 `deleteAllInBatch()`를 사용하는 규칙을 지정했다. 단, 벌크 삭제가 JPA 1차 캐시를 자동 정리하는 것은 아니므로 실제 적용 시 영속성 컨텍스트와 DB의 불일치에 주의하고 필요한 flush/clear 및 테스트 격리를 확인한다.
- **낙관적 락 버전 매핑**: `@Version` 값이 도메인 ↔ 엔티티 매핑 과정에서 누락되지 않는지 확인한다.
- **불필요한 Mock 지양**: Repository/영속성 어댑터는 실제 DB를 사용하는 통합 테스트 위주로 검증하고, 도메인 로직은 단위 테스트로 검증한다. 구현을 그대로 베낀 Mock 테스트를 피한다. Mock이 필요하면 문자열 기반 patch보다 생성자 주입 기반으로 구성한다.

### AI 연동

- Python 3.12+, PyTorch, Hugging Face Transformers(Whisper).
- Java↔Python 통신은 JPyRust(PyO3) in-process 브릿지가 1순위 검토 대상이다. GPU 분리가 필요하면 FastAPI(HTTP) 하이브리드로 전환한다.
- `AiInferenceClient` 포트 인터페이스는 구현 방식과 무관하게 유지한다.
- PoC 결과가 나오기 전에는 `AiInferenceClient`를 스텁으로 구현해 나머지 개발이 막히지 않게 한다.

### 데이터 / 인프라

- MySQL/MariaDB, Redis(캐시/필요시 Streams), AWS S3(Presigned URL 방식), Docker Compose, Nginx.

### Frontend

- React. 상태 관리는 `useState` / `useContext`를 우선하고 불필요하게 Redux 등을 제안하지 않는다.
- 입문 팀원을 고려해 MVP 화면(녹음 → 분석 중 → 결과 → 실사용 인식) 범위로 안내한다.

## 2. Git / PR 관리 규칙

- `main`은 배포 가능 상태, `develop`은 통합, `feature/*`는 기능별 개발 브랜치다. 브랜치 이름은 `CONTRIBUTING.md`에 맞춰 `feature/<기능명>`을 사용한다.
- 새 과제·기능을 시작할 때 브랜치명과 작업 완료 후 커밋·PR 생성까지의 터미널 명령어를 한 번에 안내한다. 이는 학습 안내의 단계별 설명 원칙에 대한 예외다.
- 커밋 형식: `feat/fix/refactor/docs/test/chore: 설명`.
- Atomic PR: 기능 변경과 포맷팅·린트 정리를 같은 PR에 섞지 않는다.
- 선행 PR이 아직 병합되지 않았다면 선행 브랜치를 기준으로 새 브랜치를 만들어 diff를 최소화한다.
- 모든 PR은 최소 1인 승인 필요. 백엔드는 주영이 최종 승인한다.

## 3. 보안 하드룰

- `.env`, `application.yml`, 시크릿 키, AWS credentials는 출력하거나 하드코딩하지 않는다.
- 모든 외부 입력을 검증·새니타이징하고 SQL에는 파라미터 바인딩을 사용한다.
- 인증·인가·세션·토큰 관련 변경은 항상 먼저 검토를 요청한다.
- 음성 파일·인식 결과·사용자 데이터는 민감정보로 취급한다. 본인 데이터만 조회 가능하도록 하고 S3에는 Presigned URL로 접근한다.
