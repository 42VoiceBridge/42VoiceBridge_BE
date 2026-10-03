# 개인화 train/job 계약 분석 및 설계 계획

작성 기준: 2026-10-02. 대상 브랜치 `feature/personalization-endpoints`의 `6d2cbd3`. 이 문서는 설계 계획이며 train/job 구현 완료나 AI팀과의 계약 확정을 뜻하지 않는다.

## 1. 근거와 현재 상태

| 근거 | 확인한 내용 | 설계에 미치는 영향 |
| --- | --- | --- |
| BE `.codex/context/README.md` | AI 저장소의 `docs/AI_BACKEND_CONTRACT_v1_EN.md`, `docs/openapi_ai_v1.yaml`가 요약 전달 문서보다 우선한다. 정의되지 않은 필드·경로·기본 동작을 만들지 말라고 명시한다. | HTTP 요청/응답 JSON은 AI팀이 명세에 넣기 전까지 구현하지 않는다. |
| AI `docs/openapi_ai_v1.yaml` `/v1/adapters/train` | POST는 예정 상태이며 501 응답만 정의한다. `GET /v1/adapters/jobs/{job_id}`는 OpenAPI에 없다. | 성공 응답, job 조회, 진행률을 실제 기능처럼 취급할 수 없다. |
| AI `demo/server.py` `h_train` | `not_implemented_in_demo` 501을 발생시킨다. | 현재 BE의 학습 POST 503 `TRAINING_UNAVAILABLE` 유지가 사실과 맞다. |
| AI `docs/AI_BACKEND_CONTRACT_v1_EN.md` §3.7 | 예정된 job 필드는 `job_id`, `user_id`, `base_model`, `base_revision`, `train_items[]`(녹음 ID·정답 텍스트·분할), `config`, dev 지표, adapter 식별/위치다. 요청/응답 스키마나 재시도 규칙은 없다. | 필드 목록을 데이터 요건으로만 사용하고 wire format으로 단정하지 않는다. |
| BE `.codex/context/AI_SIDE_FULL_STATUS_v2_EN.md` §4.7, §6 | 학습은 오프라인, job 조회는 404. job(`queued → running → succeeded/failed`)과 adapter(`candidate → validated → active → retired/rejected`)는 다른 생명주기다. | AI 계약 §3.7의 단일 status 열에 `validated/active/retired`까지 이어 붙인 표현을 그대로 구현하지 않는다. |
| BE `.codex/context/AI_REQUESTS_TO_BACKEND_2026-09-29_EN.md` §8~10 | 정확한 문장 원문과 문장 풀 버전이 필요하다. worker가 생기기 전에는 동의·검토된 정답·스냅샷·산출물 신원·검증·설치·활성 확인을 갖춘 수동 전달을 먼저 합의하라고 요청한다. 최소 5개는 품질 근거가 없다. | 업로드된 녹음 수만 보고 학습 job을 접수하지 않는다. |
| 현 BE 코드·API 4.2~4.4 | POST는 503, GET job은 DB 조회만 한다. GET 응답에는 명세의 `progress`가 없고, `GET /model`은 최신 COMPLETED job을 모델 보유로 간주한다. `findInProgressByUserId`는 PENDING을 찾지 않는다. | 상태 조회 계약과 실제 모델 활성 상태를 분리하고, DB 동시 요청 규칙을 설계해야 한다. |

AI 저장소의 현재 로컬 `main`은 `0e31bd3`이다. 원격의 이후 변경이나 배포 서버 상태를 확인한 결과는 아니다. AI 원본 명세가 바뀌면 이 표와 계획을 먼저 갱신한다.

## 2. train과 job이 맡을 동작

**`POST /api/v1/personalization/train`의 책임:** 인증 사용자에 대해 학습에 사용해도 되는 녹음을 확정하고, 그 시점의 입력을 재현 가능한 불변 스냅샷으로 묶는다. 최소 개수·중복 문장 선택·동의·철회·정답 검토 요건을 검증한다. 같은 사용자에게 진행 중인 요청이 없음을 DB에서 원자적으로 보장하고, BE job과 제출 기록을 함께 커밋한다. 실제 제출 worker와 AI job 관찰 경로가 준비된 경우에만 202와 BE job ID를 반환한다. 현재는 이 선행조건이 없으므로 503을 유지한다.

**`GET /api/v1/personalization/train/{jobId}`의 책임:** 본인 job만 읽고 BE에 영속화된 현재 상태·시각·실패 사유를 반환한다. GET 호출이 직접 AI 학습을 시작하거나 외부 호출의 완료를 기다리지 않게 한다. 진행률은 AI가 실제 제공하고 BE가 검증·저장할 때만 반환하며, 없으면 `null` 또는 필드 생략 중 공개 계약을 확정한다. 다른 사용자 403, 없는 job 404는 현재 동작을 유지한다.

**job worker의 책임:** 커밋된 제출 기록을 가져와 AI에 안전하게 전송하고, 응답 유실·타임아웃·재시작 뒤 중복 학습 없이 복구한다. AI 상태를 단조롭게 반영하고, 재시도 기한이 끝나면 실패 사유를 남긴다. `COMPLETED`는 학습 산출물 생성·검증 결과를 뜻하며 실사용 adapter 활성화와 같지 않다.

**adapter 관리의 책임:** 후보 산출물의 기본 모델 revision·해시·설치·readiness를 검증한다. 같은 dev 세트에서 base와 현 active adapter 모두와 비교하고 별도 절차로 승격한다. 사용자당 active adapter는 하나이며 이전 것은 롤백용으로 보존한다. 학습 데이터 삭제·동의 철회 시 파생 산출물의 retire/삭제를 추적한다.

## 3. 온라인 계약 전에 필요한 데이터

1. **정답:** 추천 기록의 `promptText`는 제안된 문장이다. 실제 낭독과 다를 수 있고, 메시지 확인 텍스트도 낭독의 정확한 전사가 아니다. 녹음별 검토된 발화 정답과 revision, 누가 언제 검토했는지를 별도로 저장한다. 미검토 녹음은 제외한다.
2. **문장 출처:** 녹음의 `promptId`·정확한 원문에 문장 풀 version/hash를 연결한다. 현 `ShownPrompt.strategyVersion`은 추천 전략 버전이고 문장 풀 버전이 아니다. AI 쪽에서 풀 버전 제공 방식이 정해질 때까지 임의로 대체하지 않는다.
3. **동의·철회:** `useForTraining=true`인 녹음만 후보로 삼고 제출 직전에 동의를 다시 확인한다. 삭제 요청 후 학습 중이거나 이미 파생된 adapter에 대한 취소·retire·AI 측 데이터 삭제 확인을 설계한다. 현재 업로드의 30일 보관 정책과 스냅샷·산출물 보관 정책도 맞춘다.
4. **스냅샷:** 녹음 ID/소유자, 정규화 WAV 해시와 접근 수단, 검토된 정답/revision, 문장 풀 버전, 전처리 버전, consent 기록, base model/revision, 학습 설정과 seed, 분할을 보존한다. BE 저장 키를 AI가 읽을 수 있다고 가정하지 않는다. 수동 export에서 AI가 실제 샘플을 읽을 수 있는지 먼저 검증한다.
5. **선정 정책:** 동일 문장 재녹음 선택과 train/dev/test 분할 주체를 합의한다. AI 원본 계약 §3.7은 split 필드를 열거하고, 전달 문서 §11은 AI 측 분할을 언급한다. 현 Goal C의 BE 분할 가정과 차이가 있으므로 결정 전에는 어느 쪽도 구현 계약으로 고정하지 않는다. 최소 5개는 임시 도메인 검증일 뿐 모델 효능 기준이 아니다.

## 4. AI팀과 확정할 train/job 계약

| 항목 | 결정이 필요한 내용 | 완료 증거 |
| --- | --- | --- |
| 사용 가능 시점·버전 | train/job 경로가 배포됐는지, 계약 버전·health/readiness에서 지원 여부를 어떻게 확인하는지 | AI 원본 계약/OpenAPI 변경 및 테스트 서버 응답 |
| 학습 제출 | 요청 필드, train/dev 데이터 전달 방식, 사용자 인증·서비스 인증, BE job ID와 AI job ID 관계, 성공 HTTP 상태·응답 | 스키마와 실제 샘플 제출 |
| 멱등성 | 같은 입력을 재전송했을 때 같은 job을 찾는 키·보존 기간, 동일 키 다른 입력의 오류, 응답 유실 후 조회 방법 | 중복 제출/타임아웃 계약 테스트 |
| 상태 조회 | job 경로, 상태 열거·전이, 즉시 성공/실패, 진행률의 유무·범위, terminal 결과·실패 코드, 404의 의미 | 성공·실패·없는 job 실서버 응답 |
| 처리 정책 | 제출·학습 기한, 재시도 가능 오류, 재시도 횟수, 취소·철회·AI 보관 데이터 삭제 | 장애·재시작 시나리오와 운영 절차 |
| 산출물 | base revision, adapter ID/revision, weights+config 해시/위치, 검증 지표, 설치·reload·active 확인 | 후보 생성에서 serving 확인까지의 재현 가능한 증거 |

위 표는 **질문 목록**이다. `adapter-training-v1-proposal`, `manifest_ref`, `Idempotency-Key` 같은 임의의 JSON 형식은 AI 문서에 없으므로 실제 outbound DTO나 HTTP 테스트로 고정하지 않는다. AI팀이 확인한 필드만 구현한다.

## 5. 실행 순서와 완료 기준

1. **계약 대조:** AI 원본 계약과 OpenAPI의 train/job 성공·오류 스키마를 합의한다. job 상태와 adapter 상태의 혼합 표현, split 소유권 차이도 정리한다. 이 단계의 산출물은 양측이 확인한 버전 있는 계약서다.
2. **학습 가능 데이터:** 검토된 낭독 정답·풀 버전·동의/철회·불변 스냅샷을 구현한다. 권한 있는 수동 export로 실제 AI 소비 형식과 삭제 절차를 검증한다. 이 단계는 온라인 학습 API 없이 진행 가능하다.
3. **job/adapter 저장 모델:** BE job과 AI job 식별자, 제출 시도·기한·오류, 스냅샷, 산출물 후보, active pointer를 분리한다. `PENDING`까지 포함한 사용자별 활성 job 제약을 MySQL에서 원자적으로 보장한다. 이력 job이 있다고 실제 모델을 사용 중이라고 추정하는 기존 `GET /model`·인식 표시도 수정한다.
4. **제출과 복구:** job+outbox를 같은 트랜잭션에 저장하고 커밋 후 worker가 AI에 제출한다. 네트워크 타임아웃 후 동일 요청을 안전하게 재시도할 방법이 확인돼야 한다. DB 잠금을 AI 호출 동안 유지하지 않는다. 동의 철회 후 제출 차단과 이미 제출된 데이터 취소를 포함한다.
5. **상태 반영:** AI job 조회/이벤트 계약에 따라 중복·역순 응답, 즉시 terminal 결과, AI의 404·501, 프로세스 재시작을 처리한다. 결과가 없는데 진척률이나 완료 산출물을 만들어 내지 않는다. 기한 초과 job은 명시적 실패로 끝낸다.
6. **공개 API·검증:** 실제 구현과 같은 작업에서 `.codex/context/API.md` 0.6, 4.2~4.4, 7.2~7.3, 변경 이력을 갱신한다. 정상 요청 202→GET 상태 변화, 부족/중복 요청, 타인 접근, MySQL 경합, 멱등 재제출, 실패·재시작·철회, 실제 AI 서버의 산출물·serving 분리를 검증한다. AI API가 여전히 501/404이면 202로 바꾸지 않고 미완료로 둔다.

## 6. 2026-10-02 준비 단계 구현 현황

- 녹음에는 검토된 발화 정답·revision·검토자/시각·문장 풀 버전을 별도 nullable 필드로 준비했다. 동의, 업로드 완료, 보관 기간, WAV 해시 및 이 필드가 모두 있어야 학습 후보로 판정한다. 현재 이 필드를 채우는 승인된 검토 절차는 없으므로 기존 녹음은 후보가 아니다.
- `PENDING`/`IN_PROGRESS` job을 포함하는 활성 슬롯의 DB 고유 제약과 활성 job 조회를 추가했다. job과 adapter 저장 모델을 분리하고, `GET /model` 및 인식 모델 선택은 활성 adapter만 참조한다. 활성화 기록은 AI 설치·serving 확인 계약이 생기기 전에는 생성되지 않는다.
- job GET의 `progress`는 실측 값이 없으므로 `null`로 반환한다. 운영 DDL은 `docs/PERSONALIZATION-RECORDING-SCHEMA.md` 및 `docs/PERSONALIZATION-JOB-ADAPTER-SCHEMA.md`에 기록했다.
- AI train 501/job 404 상태와 새 계약 부재를 확인했다. 수동 export 형식, 정답 검토 승인 절차, 분할, snapshot, 제출 worker/멱등성, AI 상태 조회와 adapter 승격은 아직 구현 대상이다. 이 항목이 확정되기 전 공개 POST는 503을 유지한다.
