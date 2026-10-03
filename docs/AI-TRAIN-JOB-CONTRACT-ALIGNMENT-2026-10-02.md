# AI–BE 개인화 학습 계약 확인 요청

작성일: 2026-10-02 · 대상: AI/Data 팀, BE 팀 · 상태: **질문서 — 아래 미확정 항목은 합의된 계약이 아님**

AI 저장소 확인: 2026-10-02 `git fetch origin` 후 로컬 `main`과 `origin/main` 모두 `0e31bd3`(2026-09-22 커밋), 앞뒤 커밋 차이 0건. 아래 AI 원본 계약·OpenAPI 판단은 이 원격 커밋까지 확인한 결과다. 별도 배포 서버의 버전과 동작은 확인하지 않았다.

## 목적과 현재 사실

BE는 개인화 녹음 업로드·삭제, 검토 정답/문장 풀 버전 저장 필드, 학습 후보 판정, job/adapter 저장 모델의 일부를 준비했다. `POST /api/v1/personalization/train`은 현재 503 `TRAINING_UNAVAILABLE`을 반환하며 job을 생성하지 않는다. BE의 job 조회는 BE DB 기록만 읽고 AI worker를 관찰하지 않는다. 검토된 정답을 입력하는 절차, 불변 스냅샷/export, AI 제출·상태 동기화·adapter 활성화는 아직 없다.

현재 확인한 AI 원본 `docs/AI_BACKEND_CONTRACT_v1_EN.md` §3.7은 `job_id`, `user_id`, `base_model`, `base_revision`, `train_items[]`, `config`, dev 지표, adapter 식별/위치 등 **예정 필드**를 열거한다. `docs/openapi_ai_v1.yaml`의 `POST /v1/adapters/train`에는 501만 있으며 job GET 경로는 없다. `demo/server.py`의 train handler도 501 `not_implemented_in_demo`다. 따라서 이 문서의 질문에 답하기 전에는 §3.7의 필드를 HTTP 요청/응답 JSON으로 해석하지 않는다. 배포된 별도 서버·새 계약이 있다면 그 버전과 위치를 알려 달라.

현재 제품 전송 방식은 **HTTP**다. 과거 전달 문서의 JPyRust/core_* 전환 제안은 별도 미래 과제이며 이번 train/job 계약의 선행조건으로 두지 않는다.

## 1. 우선 정리할 문서 간 차이

| ID | 현재 자료의 차이 | AI팀에 요청하는 결정 |
| --- | --- | --- |
| C1 | AI 원본 §3.7은 하나의 `status`에 `queued/running/failed/validated/active/retired`를 나열한다. BE에 전달된 `AI_SIDE_FULL_STATUS_v2_EN.md` §6은 **job** `queued → running → succeeded/failed`, **adapter** `candidate → validated → active → retired/rejected`를 별개로 둔다. | 실제 API/저장 계약의 job 상태와 adapter 상태를 각각 정의하고, job 성공 뒤 adapter가 거절되는 경우의 표현을 정해 달라. |
| C2 | AI 원본 §3.7의 `train_items[]`에는 `split`이 있다. `AI_SIDE_FULL_STATUS_v2_EN.md` §11은 분할을 AI 측에서 맡는다고 한다. | train/dev/test 분할 주체, 입력에 `split`이 필수인지, 분할 결과와 seed를 어느 쪽이 보존하는지 정해 달라. |
| C3 | AI 원본 §3.7은 dev 지표가 base보다 좋아야 승격할 수 있다고 한다. AI 전달 문서 §6과 `AI_REQUESTS_TO_BACKEND_2026-09-29_EN.md` §1은 **현재 active adapter와도** 같은 dev 세트로 비교하라고 한다. | 승격에 적용할 최종 비교 대상·지표·동률/악화 처리·승인 주체를 확정해 달라. |
| C4 | AI 원본 §3.7은 train/job을 “specified, not served”로 표시하지만 OpenAPI에는 성공 스키마가 없다. | 원본 계약과 OpenAPI에 동일한 버전의 성공·오류 계약을 기록하고, 실제 테스트 서버에서 확인할 시점을 알려 달라. |

## 2. 온라인 train/job을 만들기 위한 질문

**각 ID에 대해 결정값, 계약 문서/스키마 위치, 확인 가능한 예시 또는 테스트 서버 응답을 부탁한다.** 미지원이면 “미지원”과 예정 시점을 구분해 달라.

| ID | 질문 | 합의가 필요한 이유 / 확인 증거 |
| --- | --- | --- |
| T1 지원·버전 | `POST /v1/adapters/train`과 job 조회는 언제 어느 환경에서 사용 가능한가? health/readiness 또는 계약 버전으로 지원 여부를 어떻게 판별하는가? | BE가 접수 가능할 때만 202를 반환해야 한다. 배포 버전과 실제 2xx/404/501 응답이 필요하다. |
| T2 요청·응답 | train의 HTTP method/path, 인증 방식, Content-Type, 필수/선택 필드, 성공 상태·응답, BE job ID와 AI job ID의 관계는 무엇인가? `train_items[]`는 전체 데이터를 담는가, 스냅샷 참조만 담는가? | DTO를 추측하지 않기 위해 OpenAPI와 성공 요청·응답 예시가 필요하다. |
| T3 데이터 전달 | AI가 정규화 WAV를 어떻게 읽는가? BE 저장 키만으로는 접근할 수 없다. 수동 export 또는 온라인 전송의 manifest 스키마, 파일·해시 검증, 권한·만료·재전송 규칙은 무엇인가? | 실제 샘플 1건이 AI에서 열리고 해시가 일치하는 왕복 검증이 필요하다. |
| T4 정답·문장 풀 | 학습 정답은 사람이 검토한 **실제 낭독 전사**로 제한하는가? 추천 문장의 정확한 원문과 문장 풀 버전/hash는 어느 API/파일에서 제공하는가? 추천 전략 버전과 구분되는 식별자를 지정해 달라. | 제안 문장이나 발신 확인 텍스트를 자동 정답으로 쓰지 않기 위해 필요하다. |
| T5 선정·분할 | 재녹음 중복 선택, 최소 표본/길이, 사용자별 train/dev/test 분할 주체와 고정 seed, 동일 사용자 재학습 시 dev/test 재사용 정책은 무엇인가? | BE의 “최소 5개”는 임시 입력 검증이며 효능 근거가 없다. 재현 가능한 평가 계약이 필요하다. |
| T6 멱등성 | 제출 요청의 멱등 키, 같은 키·같은 입력의 반환값, 같은 키·다른 입력의 오류, 키 보존 기간은 무엇인가? 응답 유실 후 AI job을 키 또는 BE job ID로 조회할 수 있는가? | worker 재시작/타임아웃 뒤 중복 학습을 막아야 한다. 반복 제출 테스트가 필요하다. ASR의 `request_id`가 train 멱등 키라는 뜻으로 사용하지 않는다. |
| T7 상태 조회 | job GET 또는 이벤트 경로, 인증, 조회 주기/제한, 상태 enum·허용 전이, 즉시 성공/실패, terminal 결과, 없는 ID/만료 ID의 차이는 무엇인가? | BE의 `PENDING/IN_PROGRESS/COMPLETED/FAILED`에 단조롭게 대응시켜야 한다. 성공·실패·404 예시가 필요하다. |
| T8 진행률·실패 | AI가 실제 수치 진행률을 제공하는가? 없다면 `null`로 합의할 수 있는가? 실패 코드/메시지, 재시도 가능 여부, 시간 초과·서버 재시작 뒤 기한은 무엇인가? | 현재 BE 공개 GET의 `progress`는 `null`이다. 임의 진척률이나 영구 대기 job을 만들 수 없다. |
| T9 취소·철회 | 제출 전 동의 철회, 실행 중 취소, 학습 완료 후 원본·중간 산출물·adapter 삭제/retire를 각각 어떤 API/운영 절차로 확인하는가? | 학습용 음성 삭제 뒤 파생 산출물이 계속 serving되지 않도록 해야 한다. 취소·삭제 확인 응답 또는 수동 승인 기록이 필요하다. |

## 3. adapter 산출물·serving 확인 질문

| ID | 질문 | 확인 증거 |
| --- | --- | --- |
| A1 산출물 신원 | 결과에 base model과 정확한 revision, adapter ID/revision, weights **및 config** 해시, 크기, artifact 위치, 학습 스냅샷 ID가 포함되는가? | 특정 학습 입력과 산출물, 실제 추론 결과를 연결할 수 있어야 한다. |
| A2 평가·승격 | 같은 고정 dev 세트에서 base와 현재 active adapter 대비 후보의 지표를 누가 계산·검증하는가? 지표 정의, serving 전처리/디코딩 설정, 승인·거절·롤백 절차는 무엇인가? | job `COMPLETED`와 adapter `ACTIVE`를 분리한다. 이전 active 버전은 롤백용으로 보존한다. |
| A3 설치·readiness | 산출물을 누가 AI 서버에 배치하고 reload하는가? 배치 성공, 모델 로딩, 사용자별 active 선택을 어떻게 원자적으로 확인·응답하는가? 재시작 뒤 활성 상태는 어떻게 복구되는가? | BE는 AI가 실제 serving 중임을 확인한 뒤에만 활성 상태를 표시해야 한다. |
| A4 실제 사용 모델 | ASR 응답의 `model.adapter_id`/`adapter_revision`은 실제 사용한 모델을 보장하는가? `use_adapter=false`의 base 강제와 활성 adapter 사용을 동일 WAV로 어떻게 검증할 수 있는가? | BE는 요청 의도와 실제 사용 모델을 분리해 저장해야 한다. DB active 상태만으로 `PERSONALIZED`라고 기록하지 않는다. |
| A5 버전 불일치 | serving base revision이 학습 때와 다르거나 adapter가 로드되지 않았을 때 readiness와 ASR은 각각 어떤 상태·오류를 반환하는가? | 잘못된 adapter 사용이나 조용한 base 전환을 감지해야 한다. |

## 4. 오프라인 우선 전달물

온라인 worker가 아직 없다면 수동 학습 1회에 필요한 최소 전달 계약부터 합의하고 싶다. 위 T3~T5, T9, A1~A3의 답을 이용해 **동의 기록 → 검토된 정답·revision → 불변 입력 스냅샷/해시 → AI 수신 확인 → 평가 결과 → artifact 신원 → 설치·활성 확인 → 삭제/retire 확인**을 하나의 ID 체인으로 추적할 수 있어야 한다. 사람이 전달하는 단계가 있으면 담당자·파일 접근 기간·확인 기록도 명시해 달라. 수동 export가 온라인 API의 필수 선행조건이라는 뜻은 아니다.

## AI팀 답변에 포함해 주실 자료

1. 위 C1~C4, T1~T9, A1~A5 각각의 **확정 / 미지원 / 검토 중** 상태와 결정 담당자.
2. 바뀐 `docs/AI_BACKEND_CONTRACT_v1_EN.md`와 `docs/openapi_ai_v1.yaml`의 버전/커밋, 정상·실패 예시. 온라인 API가 당분간 없다면 수동 전달 스키마와 승인·삭제 절차.
3. 테스트 가능한 AI 서버 주소/버전과, 실제 또는 안전한 테스트 데이터로 확인할 수 있는 train → job → 산출물 → serving 경로. API가 501/404인 동안에는 해당 사실만 확인하고 BE 공개 POST는 503으로 유지한다.

**근거:** AI 원본 계약 §2·§3.1·§3.2·§3.7·§4, AI OpenAPI `/v1/adapters/train`, BE `.codex/context/README.md`, `.codex/context/AI_SIDE_FULL_STATUS_v2_EN.md` §4.7·§6·§11~12, `.codex/context/AI_REQUESTS_TO_BACKEND_2026-09-29_EN.md` §1~2·§8~10. 이 질문서는 기존 문서의 계획과 빈칸을 구분해 모은 것이며 새로운 AI API를 제안하거나 확정하지 않는다.
