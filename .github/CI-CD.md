# Backend CI와 인프라 CD의 경계

이 문서는 현재 `develop` 코드와 `42VoiceBridge_infra`의 Terraform 구성을 기준으로 한다. 앱 배포용 Compose와 인프라 CD는 아직 구현되지 않았다. 값의 전체 설명은 [`docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md)와 [`.env.example`](../.env.example)을 참고한다.

## BE 저장소가 게시하는 것

- `develop` 또는 `main` 대상 PR과 해당 브랜치 push에서 [`backend-ci.yml`](workflows/backend-ci.yml)이 Java 17로 `./gradlew --no-daemon clean build audioIntegrationTest`를 실행한다. `build`에는 Spotless 검사와 일반 테스트가 포함된다. MySQL 경합 테스트는 러너의 Docker에서 Testcontainers로 `mysql:8.0`을 실행하고, 오디오 테스트는 실제 FFmpeg/ffprobe를 사용한다. 테스트 보고서는 실패 시에도 14일간 artifact로 보관한다.
- 테스트가 성공하면 실행 가능한 Spring Boot JAR를 FFmpeg/ffprobe가 포함된 Java 17 런타임 이미지에 넣어 EC2 `m5.large`에 맞는 `linux/amd64` 이미지로 빌드한다. PR에서는 이미지 검증까지 실행하고, `develop`·`main` push에서만 검증한 이미지를 GHCR에 게시한다. `sha-<commit SHA>`와 브랜치 태그를 붙이고 이미지 digest를 workflow summary에 남긴다.
- `main` push에서는 이미지 게시가 성공한 뒤에만 `INFRA_DISPATCH_TOKEN`으로 Infra 저장소에 `repository_dispatch` 이벤트(`deploy-backend`)를 보낸다. payload는 `{ "ref": "refs/heads/main", "sha": "<40자리 BE 커밋 SHA>" }`이고, 이 SHA가 방금 게시한 `sha-<SHA>` 이미지의 태그다. `develop` push와 PR에서는 보내지 않는다(Infra가 `main`만 받는다). 호출이 실패하면 CI도 실패한다. 응답 204는 Infra가 요청을 받았다는 뜻이지 EC2 배포가 끝났다는 뜻이 아니다.
- `INFRA_DISPATCH_TOKEN`은 Infra 저장소의 `repository_dispatch` 호출 전용(Infra에 `Contents: write`)이다. 이미지 게시용 `GITHUB_TOKEN`, EC2의 GHCR pull 자격증명, AWS 키와 다르며 BE 저장소에는 이 토큰만 둔다.
- CD는 변경 가능한 브랜치 태그 대신 `ghcr.io/42voicebridge/42voicebridge_be@sha256:<digest>`를 배포 입력으로 사용한다. GitHub Release를 만들려면 별도 버전 태그/릴리즈 절차를 정한다. JAR·Dockerfile·Compose·환경변수 파일을 매 push마다 GitHub Release asset으로 올리지 않는다.
- Dockerfile은 이미지 제작법으로 BE 저장소에 둔다. GHCR에는 Dockerfile이 아닌 완성된 이미지가 올라간다.

## Infra 저장소가 맡을 것

- Terraform으로 생성한 EC2에 운영 Compose, 고정 이미지 digest, 일반 설정을 배치하고 이미지를 pull·재시작·검증한다. 현재 BE의 `docker-compose.yml`은 로컬 MySQL/Redis 전용이다.
- 현재 인프라 `ec2.tf`는 Docker만 설치한다. `docker compose version` 확인/설치, 8080 앱과 80/443 사이의 프록시·TLS, DB 스키마 준비(앱이 뜰 때 Flyway가 `db/migration`을 적용하고 `ddl-auto: validate`로 확인한다), 헬스 체크·롤백을 CD 전에 설계한다.
- AWS 인증은 장기 액세스 키보다 GitHub OIDC와 제한된 역할을 우선한다. 현재 Terraform에는 이 역할과 CD 워크플로가 없다. SSH는 현재 개인 IP만 허용되므로 GitHub 호스티드 러너의 직접 SSH를 기본 배포 경로로 가정하지 않는다.
- 비공개 GHCR 이미지를 pull할 경우 Infra 저장소/EC2의 package read 권한을 별도로 설정한다. 공개 이미지라면 인증 없이 pull할 수 있다.

## 값의 보관과 주입

| 구분 | 값 | 소유자 / 주입 방식 |
|---|---|---|
| BE CI | GitHub 기본 `GITHUB_TOKEN` | `contents: read`, `packages: write`로 GHCR에 게시. DB·JWT·NCP 비밀값을 CI에 주입하지 않는다. |
| BE CI → Infra | `INFRA_DISPATCH_TOKEN` (BE 저장소 Secret) | `main` 이미지 게시 후 Infra에 `deploy-backend` 이벤트를 보낼 때만 사용. 값은 로그에 남기지 않는다. |
| Infra CD 설정 | 이미지 digest, EC2 대상, AWS 리전·OIDC 역할 ARN | Infra 저장소의 변수 또는 Terraform output. 값 자체가 비밀은 아니다. |
| Infra CD 인증 | 비공개 GHCR pull 자격증명 또는 선택한 배포 채널의 자격증명 | 필요한 경우에만 Infra 저장소 GitHub Secrets/단기 토큰에 보관. AWS는 OIDC 우선. |
| 앱 런타임 비밀값 | `DB_PASSWORD`, `JWT_SECRET`, `NCP_TTS_API_KEY_ID`, `NCP_TTS_API_KEY` | DB 비밀번호는 이미 AWS Secrets Manager에 있다. 나머지도 런타임 비밀 저장소에 추가하는 방향. Spring Security는 `JWT_SECRET`을 소비할 뿐 저장하지 않는다. |
| 앱 런타임 일반 설정 | `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `REDIS_HOST`, `REDIS_PORT`, `AI_SERVER_BASE_URL`, `S3_BUCKET`, `AWS_REGION` | Infra Terraform output/AI팀 주소를 운영 Compose에서 주입. `DB_URL`은 RDS endpoint로 JDBC URL을 조합한다. |
| 오디오 실행 파일 경로 | `FFMPEG_PATH`, `FFPROBE_PATH` | 이미지에 FFmpeg/ffprobe가 포함되어 기본 PATH로 동작한다. 다른 실행 파일을 사용할 때만 지정한다. |

처음부터 BE GitHub Secrets에 앱 런타임 키를 복제할 필요는 없다. 빠른 시제품 배포에서는 JWT/NCP 키를 **Infra 저장소** GitHub Secrets에 두고 CD가 EC2에 전달할 수도 있지만, 그 경우에도 컨테이너의 실제 주입·호스트 보관 절차가 필요하다. 운영 방향은 AWS Secrets Manager를 원본으로 두고 EC2에서 읽어 제한된 파일로 준비한 뒤 Compose secrets로 마운트하는 것이다. 일반 Compose의 파일 기반 secrets는 호스트 파일을 bind mount하므로 호스트 파일 권한도 관리해야 한다.

**현재 Spring 설정은 `/run/secrets` 파일을 자동으로 읽지 않는다.** Compose secrets를 선택하면 Spring Boot `configtree:/run/secrets/` 연결과 실제 설정 바인딩 테스트를 추가해야 한다. 그 전에는 기존 문서의 EC2 `.env` 주입 방식만 코드상 바로 동작한다. 어느 방식이든 이미지 빌드 단계에 실제 API 키·DB 비밀번호를 전달하거나 이미지에 굽지 않는다. S3는 EC2 Instance Profile을 사용하므로 AWS 액세스 키를 앱 환경변수로 넣지 않는다.

현재 `application.yml`의 JWT·AI 주소·S3 버킷·NCP 키에는 로컬 개발용 기본값이 있다. `SPRING_PROFILES_ACTIVE=prod`만으로 이 값들이 모두 필수로 강제되지는 않는다. Infra CD는 필수 값의 존재를 배포 전에 확인해야 하며, 제품 배포 전에는 잘못된 기본값으로 기동하지 않도록 백엔드의 fail-fast 설정도 필요하다.

## 오디오와 이미지 검증

Ubuntu 22.04의 FFmpeg 4.4에서는 잘린 WAV가 정상 입력으로 처리되어 기존 오류 검증 테스트가 실패했다. Ubuntu 24.04의 FFmpeg 6.1 환경에서 오디오 통합 테스트 17개와 이미지 변환 검증을 통과해 이 버전을 기준으로 사용한다.

P02 오디오 변환이 포함된 현재 `develop`에 맞춰 러너와 런타임 이미지는 Ubuntu 24.04(Noble)의 FFmpeg 패키지를 사용한다. CI는 `audioIntegrationTest`를 통과한 러너와 이미지의 FFmpeg 패키지 버전이 같은지 확인한다. 버전이 다르면 게시 전에 실패하므로 두 환경의 패키지 공급 상태를 확인한다.

이미지는 기본 비루트 사용자(uid 10001)로 JAR 읽기, Java 실행, FFmpeg/ffprobe 실행, WebM → PCM16·16kHz·mono WAV 변환을 검증한다. 이 검증을 통과한 동일한 로컬 이미지를 push하며 GHCR 로그인과 게시는 push 이벤트에서만 수행한다. PR에는 앱 런타임 비밀값이나 GHCR 로그인 정보가 필요하지 않다.

이미지 검증은 외부 MySQL/Redis/AI/S3에 연결한 운영 기동 검증을 대체하지 않는다. 실제 배포 후 연결과 헬스 체크는 Infra CD에서 수행한다.
