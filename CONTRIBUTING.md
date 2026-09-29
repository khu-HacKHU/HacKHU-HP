# 기여 가이드

> 팀원이 실제로 따를 수 있는 최소한만 담았습니다. 필요해지면 그때 추가합니다.

- **이슈 관리**: GitHub Projects 보드 하나가 백로그의 단일 원본이다. 단톡방에서 정한 것도 이슈에 남긴다.
- **WIP 제한**: 한 사람이 동시에 `In Progress`로 진행하는 이슈는 최대 2개다.

---

## 1. 커밋 메시지

형식: `type: 한글 설명`

```
feat: 공지 목록 조회 API 구현
fix: 로그인 후 무한 리다이렉트 수정
security: 게시글 수정 API에 작성자 검사 추가
```

| type | 용도 |
|---|---|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `security` | 취약점 수정, 보안 설정 강화 (S4 Exploit 수정 포함) |
| `docs` | 문서만 변경 |
| `design` | UI·스타일 수정 (동작은 그대로, 보이는 결과만 바뀜) |
| `cicd` | 배포, CI/CD, 보안 스캔 워크플로 변경 |
| `refactor` | 동작 변경 없는 코드 구조 개선 |
| `test` | 테스트 추가·수정. 프로덕션 코드 변경은 포함하지 않는다 |
| `chore` | 설정, 의존성, 그 외 유지보수 |
| `release` | 출시 PR (`release/vX.Y.Z → main`) 전용 |

- scope는 쓰지 않는다. 어느 영역인지는 diff가 보여준다.
- 제목은 **72자** 안에서 쓴다. squash merge가 ` (#123)`을 덧붙이므로 여유가 필요하다.
- 제목에 이슈 번호를 넣지 않는다. 이슈 연결은 브랜치명과 PR 본문이 한다.
- 본문이 필요하면 빈 줄 하나 띄우고 **왜** 바꿨는지 위주로 쓴다.

### 형식을 검사하는 것

- **관문은 CI다.** squash merge에서 히스토리에 남는 것은 PR 제목이므로 `Lint PR` 워크플로가 그것을 검사한다.
- **로컬 훅은 편의다.** 저장소 루트에서 `npm install`을 한 번 하면 `commit-msg` 훅이 설치된다. 설치하지 않아도 막히지 않는다.
- 규칙의 원본은 이 문서이고 [`commitlint.config.mjs`](commitlint.config.mjs)가 그것을 옮긴 것이다. 둘은 같은 PR에서 함께 고친다.

---

## 2. 브랜치 전략

```text
feat/12-notice-list ──PR──> develop
                               └── release/v0.1.0 ──PR──> main
fix/31-login-loop   ──PR──> develop
                    release/v0.1.0 <──PR── fix/42-release-blocker
```

- 작업 브랜치: `{type}/{issue-number}-{slug}` (예: `feat/12-notice-list`)
- 출시 브랜치: `release/v{major}.{minor}.{patch}` (예: `release/v0.1.0`)
- 이슈 번호는 `#` 없이 숫자만, slug는 영어 kebab-case로 쓴다.
- 분기 전에 `git fetch origin`을 하고 `origin/develop`에서 자른다.
- `main`에는 `release/vX.Y.Z` 브랜치만 PR을 보낸다. CI가 출발 브랜치를 검사한다.
- release 브랜치를 만든 뒤에는 새 기능을 넣지 않고 출시를 막는 수정만 반영한다. release에만 들어간 수정은 출시 후 `release → develop` PR로 되돌린다.
- 오래 사는 브랜치는 만들지 않는다. 며칠 안에 머지가 안 되면 범위를 쪼갠다.

---

## 3. PR 규칙

| 방향 | 머지 방식 |
|---|---|
| 작업 브랜치 → `develop` | Squash merge |
| 수정 브랜치 → `release/vX.Y.Z` | Squash merge |
| `release/vX.Y.Z` → `main` | Merge commit (어느 버전이 언제 배포됐는지 남긴다) |
| `release/vX.Y.Z` → `develop` | Squash merge |

- **리뷰**: 같은 팀원 1명 이상의 승인이 필요하다. 코드 오너 경로(`infra/`, `.github/` 등)는 코드 오너 승인도 필요하다.
- **PL 최종 승인**: 인증·파일 업로드·권한 관련 PR.
- **필수 검사**: `CI / ci-ok`, `Lint PR / pr-title`, `Security / gitleaks`.
- **보안 스캔 결과**: Semgrep·Checkov·CodeQL은 PR을 막지 않지만, 새로 생긴 경고는 PR에서 확인하고 오탐이면 이유를 적는다.
- **작성자 책임**: PR 작성자는 모든 변경을 본인이 설명할 수 있어야 한다.
- **이슈 연결**: 본문에 `Closes #12`를 적는다.
- **API 변경**: [API 규약](docs/conventions/api.md)을 따르고, API 명세를 같은 PR에서 갱신한다.

---

## 4. 보안 도구 결과 다루기

자세한 내용은 [docs/security/tools.md](docs/security/tools.md)에 있다.

- gitleaks가 PR을 막았다면 **커밋에서 지우는 것만으로는 부족하다.** 해당 키를 즉시 폐기·재발급하고 PL에게 알린다.
- 오탐 허용(`.gitleaks.toml`, `nosemgrep`, `checkov:skip`)을 추가할 때는 PR 본문에 근거를 적는다.
