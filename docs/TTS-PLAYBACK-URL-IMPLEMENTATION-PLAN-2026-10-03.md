# TTS 재생 URL 문제 분석 및 구현 계획

- 작성일: 2026-10-03
- BE 분석 기준: 조회한 `origin/develop` (`e222c29`). 현재 작업 공간은 이전 커밋의 detached HEAD이므로 구현은 최신 develop에서 새 브랜치로 시작한다.
- 상태: 2026-10-03 사용자 요청에 따라 BE 구현·명세 갱신·로컬 검증 완료. 실제 AWS·NCP·브라우저 검증 및 인프라 수정은 미실행.
- 담당: Confirmation·TTS 담당 주영이 구현을 주도하고, 공통 저장소 변경은 A·B와 영향 범위를 확인한다. 인식→확인→재생은 태원·주영·FE의 공동 검증 범위다.

## 1. 확인한 문제

현재 흐름은 다음과 같다.

```text
Clova 합성 → S3StorageAdapter.upload()
          → recordings/{uuid}.mp3 반환
          → TtsRequest.markCompleted(key)
          → tts_requests.audio_url 저장
          → GET /tts/{ttsId}의 audioUrl로 key 반환
```

`StorageKeys`는 파일 확장자만 반영하여 `recordings/` 아래 key를 만든다. `TtsSynthesisHandler`가 이를 URL로 취급하고 `GetTtsStatusService`가 변환 없이 응답한다. 저장 완료 상태와 브라우저에서 접근 가능한 재생 주소가 혼동되어 있다. API 명세 6.2의 HTTPS URL 예시와 실제 응답도 다르다.

`LocalFileStorageAdapter`도 key만 반환하며 파일 serving 경로가 없다. 로컬 프로파일의 저장 성공도 브라우저 재생 성공을 보장하지 않는다. S3 업로드 요청에는 MP3 Content-Type을 명시하지 않는다.

인프라 로컬 코드에서 확인한 사항:

- `../42VoiceBridge_Infra/infra/environments/dev/2_storage/s3.tf`: public access block의 네 설정이 모두 true다.
- `../42VoiceBridge_Infra/infra/environments/dev/3_application/iam.tf`: EC2 역할에 해당 버킷 객체의 `s3:GetObject`, `s3:PutObject`가 선언되어 있다.
- 확인한 코드 범위에는 CloudFront 배포·S3 CORS 구성이 없다.
- 이는 선언된 설정에 대한 확인이다. 실제 배포 상태, 역할 적용 여부, IAM deny·네트워크 제한·암호화 권한은 검증하지 않았다.

NCP ID/KEY 주입 후 합성·저장 가능성은 코드 경로에 대한 판단이다. 실제 NCP 합성, S3 저장, FE 재생 성공은 각각 별도로 확인해야 한다.

## 2. 권장 설계

**DB에는 object key를 보존하고, 본인 TTS 조회 시 짧은 유효기간의 presigned GET URL을 발급한다.**

```text
GET /tts/{ttsId}
  → 요청·confirmation 조회
  → 소유권 확인
  → COMPLETED일 때 저장 key로 URL 발급
  → audioUrl에 HTTPS URL 반환
  → FE가 S3에서 MP3 재생
```

기존 `StoragePort.upload()`의 key 반환 계약은 유지한다. 녹음·진단 등 공통 호출자에 URL을 저장하게 만들지 않는다. S3 URL 생성은 outbound adapter에 두고 application·domain에 AWS SDK 의존성을 넣지 않는다.

서명 URL을 DB에 저장하지 않는다. 매 조회에서 유효한 URL을 발급하여 만료 후 다시 조회하면 재생할 수 있게 한다. 초기 TTL은 10분을 제안하며 설정으로 관리한다. 실제 만료는 서명 자격증명 만료 등으로 더 짧아질 수 있다. URL을 가진 사람은 유효기간 동안 접근할 수 있으므로 URL은 접근 자격으로 취급하고 로그에 전체 값을 남기지 않는다.

CloudFront는 기존 배포가 없어 이번 기본안으로 선택하지 않는다. 버킷 공개 전환이나 key에 HTTPS 접두사만 붙이는 방식도 해결책으로 사용하지 않는다.

근거: [S3 private 객체 공유](https://docs.aws.amazon.com/AmazonS3/latest/userguide/ShareObjectPreSignedURL.html), [Java SDK presigned GET 예시](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.html).

## 3. 단계별 구현 범위

### 1단계 — 내부 저장값 의미 정리

대상: `TtsRequest`, `TtsSynthesisHandler`, `TtsRequestJpaEntity`, `TtsRequestPersistenceAdapter` 및 관련 테스트.

- 내부 필드·변수·getter를 `audioStorageKey` 의미로 정리한다.
- 첫 구현에서는 JPA `@Column(name = "audio_url")`로 기존 DB 컬럼을 유지하여 배포 전 DDL 변경을 줄인다. 공개 응답의 `audioUrl` 이름도 유지한다.
- 기존 DB 값이 실제 key인지 배포 전 점검한다. 기존 정상 `recordings/{uuid}.mp3` 값은 그대로 사용한다. HTTP URL 등 다른 형식이 발견되면 별도 변환·복구 절차를 정하고 임의로 다른 버킷이나 URL에 서명하지 않는다.
- COMPLETED의 비어 있거나 잘못된 key는 내부 데이터 오류로 처리한다. key를 재생 URL처럼 그대로 내려주지 않는다.

### 2단계 — URL 발급 포트와 S3 구현

대상: 신규 `AudioPlaybackUrlPort` 및 S3 구현체, `S3Config`, `application.yml`, S3 업로드 어댑터.

- 포트는 저장 key를 받아 재생 URL을 반환한다. AWS 타입은 포트에 노출하지 않는다.
- `S3Presigner` 빈을 등록하고 기존 S3와 동일한 리전·버킷·자격증명 경로를 사용한다. 빈 종료 시 presigner를 닫는다.
- `voicebridge.tts.playback-url-ttl`을 제안한다. 기본 10분 및 양수·지원 범위 검증을 추가한다.
- 발급 대상은 서버가 보존한 정상 MP3 key만 허용한다. FE가 bucket·key·임의 URL을 지정하지 않는다.
- MP3 업로드에 `audio/mpeg`를 지정한다. WAV 등 다른 공통 저장 호출의 형식도 함께 확인한다. 기존 MP3는 presigned GET의 응답 Content-Type 재정의로 처리할 수 있게 한다.
- URL 생성은 파일 존재·실제 접근 성공을 확인하는 작업이 아니다. 매 조회에 S3 HEAD를 추가할지는 이번 기본안에서 제외하고 실제 다운로드 검증으로 접근 가능성을 확인한다.

### 3단계 — TTS 조회에 연결

대상: `GetTtsStatusService`, 관련 service·web 테스트, API 명세 6.2.

- 기존 소유권 확인 후에만 URL 발급 포트를 호출한다. 타인 요청 403, 없는 요청 404를 유지한다.
- PENDING·FAILED는 URL을 발급하지 않고 `audioUrl: null`을 반환한다.
- COMPLETED는 새 URL을 반환한다. URL 생성 실패가 합성 상태를 FAILED로 바꾸지 않게 한다. 영속 상태는 유지하고 조회 요청만 실패시킨다.
- 초기 오류 계약은 기존 `INTERNAL_SERVER_ERROR`(500) 사용을 제안한다. 잘못된 저장 key 또는 서명 자격증명·설정 문제를 클라이언트 오류로 처리하지 않는다. 구현 시 예외 번역 범위를 확인한다.
- 별도 만료시각 응답 필드는 우선 추가하지 않는다. FE는 URL 만료 시 GET을 다시 호출한다. 정확한 만료 안내가 필요하면 응답 필드 추가를 별도 계약으로 합의한다.

### 4단계 — 로컬 재생 경로

로컬에서도 실제 FE 재생을 확인하려면 URL 발급 mock만으로는 부족하다. 개발용 private S3 또는 인증된 로컬 파일 serving 중 하나가 필요하다.

기본 로컬안은 `GET /tts/{ttsId}/audio` 파일 serving과 로컬 URL 어댑터다. 서버가 ttsId로 key를 찾고 소유권·완료 상태를 확인한 뒤 파일을 읽는다. 사용자 경로 입력을 허용하지 않고 저장 루트 밖 접근을 막는다. `audio/mpeg`와 FE 플레이어에 필요한 Range 처리를 검증한다.

단, `<audio src>`는 일반적인 Bearer 헤더를 직접 넣지 못하므로 인증 전달 방식이 선행 결정이다. FE의 인증된 fetch→Blob 재생 또는 짧은 유효기간의 ttsId 결합 재생 토큰을 비교하고 하나를 정한다. 인증 없이 uploads 디렉터리를 공개하는 방식은 채택하지 않는다. 새 엔드포인트를 선택하면 API 6.2 인접 절에 요청·인증·상태·오류를 추가한다.

운영 S3 URL 제공과 로컬 serving은 독립 단계로 구현할 수 있으나, 로컬 재생 경로가 정해지기 전 전체 환경 재생 완료로 표시하지 않는다.

### 5단계 — 명세·배포·FE 연결

- `.codex/context/API.md` 6.2에 상태별 URL 유무, 임시 URL·재조회 규칙, 본인 데이터 권한, 403·404·500을 기록하고 변경 이력을 갱신한다. 공통 오류 코드 추가·변경이 있으면 0.6도 갱신한다.
- `docs/DEPLOYMENT.md`에 TTL 설정, S3 GetObject 권한, 리전·버킷 일치 조건을 기록한다.
- 실제 EC2 역할 권한을 확인한다. 서명 URL 자체는 생성돼도 다운로드가 403이면 권한·버킷 정책·KMS·자격증명 만료·네트워크 제한을 점검한다.
- S3 CORS는 FE의 접근 방식에 맞춰 확인한다. 단순 audio 재생과 fetch·Web Audio·crossOrigin 사용의 요구가 다르므로 무조건 필요하거나 불필요하다고 가정하지 않는다. 필요하면 허용 origin·GET/HEAD·요청 헤더를 인프라 담당이 제한하여 설정한다.
- 무효화된 confirmation이나 사용자가 취소한 요청이 재생되지 않아야 하는 제품 규칙을 다시 확인한다. 기존 GET은 소유권만 검사하며, 이미 발급된 S3 URL은 즉시 철회하기 어렵다. 엄격한 즉시 철회가 필요하면 signed URL만으로 충족했다고 판단하지 않고 BE serving 등의 대안을 선택한다.
- FE는 최신 선택 요청만 재생하고, 오래된 폴링 응답이나 취소 이후 응답으로 재생하지 않게 한다.

## 4. 검증 계획

| 범위 | 완료 증거 |
| --- | --- |
| 저장 | 합성된 MP3 key 저장, DB 왕복, 기존 컬럼 호환, audio/mpeg 설정 |
| URL 발급 | 올바른 bucket·key·리전·GET·TTL 서명, 잘못된 key·설정 거절, 서명 생성 오류 번역 |
| 권한·상태 | 타인·없는 요청·PENDING·FAILED에서 URL 발급 미실행, 본인 COMPLETED만 URL 응답 |
| 오류 분리 | 발급 실패 후에도 저장된 TTS 상태는 COMPLETED 유지 |
| 만료 | 만료 전 실제 S3 GET 성공, 만료 후 거절, GET 재조회 후 새 URL로 성공 |
| 로컬 | 인증 방식 확정, 루트 밖 접근 거절, 필요 Range 응답, FE 재생 |
| 제품 흐름 | NCP 합성→S3 업로드→TTS 조회→브라우저 MP3 재생 및 취소·무효화 처리 |
| 계약 | 공개 응답·상태·오류와 API 명세 일치, 기존 인식·진단·개인화 key 저장 회귀 없음 |

단위·web 테스트에서는 실제 AWS 접근 없이 검증하고, SDK 서명 검증에는 테스트 자격증명을 사용한다. 실제 S3·NCP·브라우저 검증은 별도 결과로 기록한다. 일반 테스트 통과를 실서비스 재생 성공으로 표현하지 않는다.

## 5. 구현 전 확정할 사항과 완료 기준

1. 기본 운영 방식: private S3 + 조회 시 presigned URL. 기본 TTL 10분은 제안값이다.
2. 로컬 재생 인증 방식과 새 serving API 범위를 FE·TTS 담당과 확정한다.
3. confirmation 무효화·취소 후 이미 발급된 URL에 요구되는 철회 수준을 확정한다.
4. DB 기존값과 실제 배포 IAM·버킷 정책을 점검한다.
5. 구현·명세 갱신·회귀 검증과 실제 브라우저 재생이 끝나면 완료로 표시한다. 미검증 환경과 미결정 정책은 명시한다.

이 문서는 계획 산출물이다. 현재 코드 수정, 배포 설정 변경, 커밋·푸시·팀 메시지 게시를 수행하지 않았다.


## 6. 구현 결과 (2026-10-03)

- worktree를 추가하지 않고 현재 디렉터리에 `origin/develop` 기준 `fix/tts-playback-url` 브랜치를 생성했다.
- `AudioPlaybackUrlPort`와 S3 presigner 어댑터를 추가했다. 조회 시 GET URL을 발급하며 TTL 기본 10분, 양수 정수 초·최대 7일을 검증한다.
- TTS 내부 필드를 `audioStorageKey`로 정리하고 DB `audio_url` 컬럼을 유지했다. public `audioUrl` 응답은 접근 URL이다.
- MP3 업로드 및 presigned GET 응답에 audio/mpeg를 지정했다. 공통 StoragePort.upload의 key 반환 계약을 유지했다.
- 소유권 확인 후 완료 상태에서만 URL을 발급한다. 무효화된 confirmation은 409이며 발급 실패는 COMPLETED 상태를 변경하지 않는다. 조회 응답은 no-store다.
- local은 인증된 `/api/v1/tts/{ttsId}/audio`를 제공한다. FE는 Bearer fetch 후 Blob으로 재생한다. 서버는 파일 key 검증과 실제 경로·symlink 이탈 검사를 수행한다. 정상 200, Range 206 및 잘못된 범위 416을 테스트했다.
- 로컬 설정 URL, TTL, 기존 DB값 배포 전 점검, AWS 권한·CORS·만료·무효화 제한을 API 6.2·6.3 및 DEPLOYMENT 문서에 기록했다.
- Java 17에서 `./gradlew spotlessApply build audioIntegrationTest --offline` 성공. 일반 테스트 391개, FFmpeg 통합 테스트 18개: 실패·오류·건너뜀 0개. `git diff --check` 통과.
- 실제 S3 URL 접근·만료 후 거절·NCP 합성·브라우저 재생은 미검증이다. 선언된 GetObject 권한이 실제 배포에 적용됐는지 확인이 필요하다. 즉시 URL 철회는 구현하지 않으며 기존 발급 URL은 만료까지 사용할 수 있다.
- 기존 PROGRESS 메모와 RecognitionPersistenceAdapter 주석을 복원했다. 이전 untracked 문서와 develop의 문서가 다른 경우 원본 stash를 보관했다. 커밋·푸시는 수행하지 않았다.


## 7. 아키텍처 리뷰 개선 반영 (2026-10-03)

- 공통 소유권·confirmation 유효성 검사를 `TtsPlaybackAccess`로 추출했다. 상태 조회와 파일 조회가 각각 이 객체를 사용하며 구체 상태 조회 서비스의 내부 메서드에 의존하지 않는다.
- `LocalTtsAudioPort`를 `TtsAudioReadPort`로 바꾸고 `GetLocalTtsAudioService`를 환경 중립적인 `GetTtsAudioService`로 정리했다.
- application의 local 프로파일 조건을 제거하고 `LocalTtsPlaybackConfig`에서 local일 때만 파일 조회 유스케이스를 등록한다.
- 공개 API·HTTP 상태·오류 코드는 변경하지 않았다.
- TTS·저장소 대상 테스트, local/prod별 빈 등록 테스트, spotlessCheck 및 git diff --check가 통과했다. 이전 전체 빌드·오디오 검사 결과와 이번 대상 테스트 결과를 구분한다.
