# 기여 가이드

> 팀원이 실제로 따를 수 있는 최소한만 담았습니다. 필요해지면 그때 추가합니다.

- **이슈 관리**: GitHub Projects 보드 하나가 백로그의 단일 원본이다. 단톡방에서 정한 것도 이슈에 남긴다.
- **이슈 제목**: 커밋 메시지와 같은 `type(scope): 한글 설명` 형식으로 쓴다 (1절). 예: `feat(board): 공지 목록 조회`
- **WIP 제한**: 한 사람이 동시에 `In Progress`로 진행하는 이슈는 최대 2개다.

---

## 1. 커밋 메시지

형식: `type(scope): 한글 설명`. scope는 선택이다.

```
feat(board): 공지 목록 조회 API 구현
fix(auth): 로그인 후 무한 리다이렉트 수정
security(board): 게시글 수정 API에 작성자 검사 추가
chore: 의존성 업데이트
```

| type | 용도 |
|---|---|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `security` | 취약점 수정, 보안 설정 강화 (S5 Exploit 수정 포함) |
| `docs` | 문서만 변경 |
| `design` | UI·스타일 수정 (동작은 그대로, 보이는 결과만 바뀜) |
| `cicd` | 배포, CI/CD, 보안 스캔 워크플로 변경 |
| `refactor` | 동작 변경 없는 코드 구조 개선 |
| `test` | 테스트 추가·수정. 프로덕션 코드 변경은 포함하지 않는다 |
| `chore` | 설정, 의존성, 그 외 유지보수 |
| `release` | 출시 PR (`release/vX.Y.Z → main`, `hotfix/vX.Y.Z → main`) 전용 |

| scope | 영역 |
|---|---|
| `auth` | 인증·권한 팀 |
| `board` | 게시판 팀 |
| `gallery` | 갤러리 + 인프라 팀의 갤러리 기능 |
| `infra` | 갤러리 + 인프라 팀의 Terraform, 배포 |

- scope는 팀 영역이다. 모노레포라 어느 팀의 변경인지 제목에서 바로 보이게 한다.
- 공통 설정, 문서, CI처럼 팀에 속하지 않는 변경은 scope를 생략한다.
- 제목은 **72자** 안에서 쓴다. squash merge가 ` (#123)`을 덧붙이므로 여유가 필요하다. Dependabot이 올린 PR은 제목을 우리가 만들지 않으므로 길이만 검사하지 않는다 (type과 scope는 그대로 검사한다).
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
main(v0.1.0) ── hotfix/v0.1.1 ──PR──> main (v0.1.1)
                              └─PR──> develop
```

- 작업 브랜치: `{type}/{issue-number}-{slug}` (예: `feat/12-notice-list`). type은 1절 목록에서 `release`를 뺀 것이다. CI가 develop, release 대상 PR의 출발 브랜치 이름을 검사한다.
- 출시 브랜치: `release/v{major}.{minor}.{patch}` (예: `release/v0.1.0`)
- 긴급 수정 브랜치: `hotfix/v{major}.{minor}.{patch}` (예: `hotfix/v0.1.1`). 운영 중인 버전의 긴급 수정만 다룬다
- 이슈 번호는 `#` 없이 숫자만, slug는 영어 kebab-case로 쓴다.
- 분기 전에 `git fetch origin`을 하고 `origin/develop`에서 자른다. hotfix만 `origin/main`에서 자른다.
- `main`에는 `release/vX.Y.Z`, `hotfix/vX.Y.Z` 브랜치만 PR을 보낸다. CI가 출발 브랜치를 검사한다.
- release 브랜치를 만든 뒤에는 새 기능을 넣지 않고 출시를 막는 수정만 반영한다. release에만 들어간 수정은 출시 후 `release → develop` PR로 되돌린다.
- hotfix는 main에 머지한 뒤 같은 브랜치에서 `hotfix → develop` PR을 보내 develop에도 반영한다.
- release와 hotfix 흐름은 PL이 맡는다.
- **버전 태그**: main에 머지된 커밋에 `vX.Y.Z` 태그를 단다. GitHub Releases에서 대상 `main`으로 만들거나 `git tag -a vX.Y.Z -m "release: vX.Y.Z"` 후 push한다. release 브랜치의 마지막 커밋에 미리 달지 않는다.
- 오래 사는 브랜치는 만들지 않는다. 며칠 안에 머지가 안 되면 범위를 쪼갠다.

---

## 3. PR 규칙

| 방향 | 머지 방식 |
|---|---|
| 작업 브랜치 → `develop` | Squash merge |
| 수정 브랜치 → `release/vX.Y.Z` | Squash merge |
| `release/vX.Y.Z` → `main` | Merge commit (어느 버전이 언제 배포됐는지 남긴다) |
| `release/vX.Y.Z` → `develop` | Squash merge |
| `hotfix/vX.Y.Z` → `main` | Merge commit |
| `hotfix/vX.Y.Z` → `develop` | Squash merge |

Rebase and merge는 쓰지 않는다.

- **리뷰**: 같은 팀원 1명 이상의 승인이 필요하다. 코드 오너 경로(`infra/`, `.github/` 등)는 코드 오너 승인도 필요하다.
- **PL 최종 승인**: 인증·파일 업로드·권한 관련 PR.
- **PR 크기**: 리뷰어가 30분 안에 읽을 수 있는 크기로 만든다. 넘으면 PR을 쪼갠다.
- **리뷰 쓰는 법**: 지적에 결함 심각도 등급(`P1` 피해가 간다, `P2` 운영에서 문제가 된다, `P3` 아무 일도 안 생긴다)을 붙인다. P1이 남으면 Request changes, P2만 남으면 Request changes이지만 후속 이슈로 합의하면 Approve, P3만 남으면 Approve다. 본문 구조와 스레드를 누가 닫는지까지 [코드 리뷰 규약](docs/conventions/code-review.md)에 있다.
- **리뷰 반영**: 수정은 새 커밋으로 올린다. force push로 덮어쓰지 않는다. 어차피 squash merge로 합쳐진다. 반영한 커밋 SHA와 바꾼 내용을 스레드에 적는다.
- **라운드 상한**: 반영과 재리뷰 3라운드를 마쳤는데도 지적이 남으면 P1만 그 PR에서 고치고, P2와 P3는 리뷰어가 후속 이슈로 넘기고 머지한다. PL 최종 승인 대상 PR의 보안 관련 P2는 넘기지 않는다.
- **대화 해결**: 미해결 스레드가 하나라도 있으면 머지가 막힌다. 리뷰어의 P1·P2 지적은 리뷰어가 닫고, P3와 보안 도구 봇의 경고는 작성자가 닫는다. P3는 받아들이지 않고 닫아도 되며 그때는 이유를 한 줄 남긴다.
- **필수 검사**: `CI / ci-ok`, `Lint PR / pr-title`, `Security / gitleaks`.
- **보안 스캔 결과**: Semgrep·Checkov·CodeQL은 PR을 막지 않지만, 새로 생긴 경고는 PR에서 확인하고 오탐이면 이유를 적는다.
- **작성자 책임**: PR 작성자는 모든 변경을 본인이 설명할 수 있어야 한다.
- **이슈 연결**: 본문에 `Closes #12`를 적는다. 빠지면 CI가 막는다. 검사가 받는 키워드는 `Closes`, `Fixes`, `Resolves`와 그 변형(`close`, `closed`, `fix`, `fixed`, `resolve`, `resolved`)뿐이다. GitHub이 머지할 때 이슈를 닫는 키워드와 같은 목록이고, `Ref`와 `See`는 받지 않는다. 이슈 하나는 PR 하나가 끝낸다. Dependabot이 올린 PR과 이 저장소의 `release/*` 브랜치 PR은 검사하지 않는다. 예외는 PR 작성자와 포크 여부로 판정하므로 브랜치 이름만 흉내 내도 받을 수 없다. `hotfix/*`는 예외가 아니다. 고치는 버그의 이슈를 만들고 그 번호를 적는다.
- **쪼갠 PR의 이슈**: 이슈 하나를 PR 여러 개로 쪼개면 **쪼갠 쪽에도 이슈를 만들고** 그 번호로 브랜치와 `Closes #`를 쓴다. 원래 이슈에서 쪼갰다는 것을 새 이슈 본문에 적는다. 원래 이슈를 `Closes`로 닫으면 남은 작업이 있는데 이슈가 닫히고, `Ref`로 걸어 두면 CI가 막는다.
- **API 변경**: [API 규약](docs/conventions/api.md)을 따르고, API 명세를 같은 PR에서 갱신한다.

---

## 4. 보안 도구 결과 다루기

자세한 내용은 [docs/security/tools.md](docs/security/tools.md)에 있다.

- gitleaks가 PR을 막았다면 **커밋에서 지우는 것만으로는 부족하다.** 해당 키를 즉시 폐기·재발급하고 PL에게 알린다.
- 오탐 허용(`.gitleaks.toml`, `nosemgrep`, `checkov:skip`)을 추가할 때는 PR 본문에 근거를 적는다.

---

## 5. PR·이슈 레이블

**레이블은 워크플로가 붙인다.** `PR Labels`, `Issue Labels` 워크플로가 제목과 영역으로 레이블을 자동으로 붙이고, 제목을 고치면 레이블도 따라 바뀐다. 사람이 정하는 것은 이슈 본문의 영역 체크박스뿐이다.

**type 레이블** — 제목의 type으로 정한다. 하나만 남고, 다른 type 레이블을 손으로 붙이면 다음 실행에서 떼어진다.

| 제목 type | 레이블 |
|---|---|
| `feat` | `feature` |
| `fix` | `bug` |
| `docs` | `documentation` |
| `security`, `design`, `cicd`, `refactor`, `test`, `chore`, `release` | type과 같은 이름 |

**영역 레이블** (`backend`, `frontend`, `infra`) — 붙이기만 하고 떼지 않는다. 손으로 붙여도 그대로 둔다.

| | `backend` | `frontend` | `infra` |
|---|---|---|---|
| PR | `backend/` 아래 파일을 바꿈 | `frontend/` 아래 파일을 바꿈 | `infra/` 아래 파일을 바꿈, 또는 scope가 `infra` |
| 이슈 | 본문에 `- [x] backend` | 본문에 `- [x] frontend` | scope가 `infra` |

**검사**
- PR: 레이블이 제목·경로와 맞지 않으면 필수 검사 `pr-labels`가 실패해 머지가 막힌다.
- 이슈: 제목 형식이 틀리거나, 기능·버그 이슈(`feature`, `bug`)에 영역 레이블이 하나도 없으면 봇이 이슈에 안내 댓글을 단다. 고치면 댓글이 지워진다.

**그 밖에**
- Dependabot PR은 워크플로가 레이블을 바꿀 수 없어서 [dependabot.yml](.github/dependabot.yml)의 `labels`로 미리 붙인다. 그래서 `PR Labels` 워크플로는 Dependabot PR에서 아예 돌지 않는다.
- 기존 이슈에 한 번에 적용할 때는 PL이 로컬에서 `DRY_RUN=1 bash .github/scripts/backfill-issue-labels.sh`로 미리 본 뒤 `DRY_RUN` 없이 돌린다.
- 출시 때 GitHub 자동 릴리스 노트가 이 레이블로 항목을 나눈다 ([release.yml](.github/release.yml)).
- 규칙의 원본은 이 절이고, [`labels.sh`](.github/scripts/labels.sh)가 그것을 옮긴 것이다. 둘은 같은 PR에서 함께 고친다.
