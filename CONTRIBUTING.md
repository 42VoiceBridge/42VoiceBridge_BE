# 브랜치 전략 & 커밋 컨벤션

## 브랜치 전략

- `main`: 배포 가능 상태만 유지. **직접 커밋/푸시 금지, 항상 PR로만 병합.**
- `develop`: 통합 브랜치. 모든 feature 브랜치가 여기로 모인다.
- `feature/<기능명>`: 모든 기능 개발은 `develop`에서 분기해서 진행한다.
  - 예: `feature/auth`, `feature/diagnosis-session`, `feature/recommendation`
  - 선행 PR이 아직 머지되지 않았다면 `develop`이 아니라 그 브랜치를 기준으로 새 브랜치를 따서 diff를 최소화한다.

작업 흐름:

```
git checkout develop
git pull
git checkout -b feature/<기능명>
# 작업 후
git push -u origin feature/<기능명>
# GitHub에서 develop을 base로 PR 생성 → 리뷰 승인 후 병합
```

## 커밋 컨벤션

```
<type>: <설명>
```

| type | 의미 |
|---|---|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| refactor | 동작 변화 없는 코드 개선 |
| docs | 문서 변경 |
| test | 테스트 추가/수정 |
| chore | 빌드, 설정 등 기타 변경 |

예: `feat: 진단 세션 생성 API 추가`

## 코드리뷰

- 모든 PR은 최소 1인 승인 필요, 백엔드는 최종적으로 팀장 승인.
- 기능 변경과 포맷팅/린트 정리는 같은 PR에 섞지 않는다(Atomic PR).
