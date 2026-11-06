# HacKHU-HP

HacKHU 동아리 홈페이지. 인증·권한, 게시판, 갤러리를 만들면서 보안 도구(SAST·SCA·IaC·DAST 등)를 스프린트별로 돌려 보고 직접 취약점을 판별·수정하는 교육형 프로젝트다.

AI 코딩 도구(Codex, Claude Code, Cursor 등)가 공통으로 읽는 작업 지침이다. 영역별 세부 규칙은 각 디렉터리의 `AGENTS.md`에 있다.

## 구조

모노레포. 현재 상태·일정·역할은 [README.md](README.md)가 진입점이다.

```
frontend/   웹 프론트엔드 — React 19 / TypeScript / Vite
backend/    API 서버 — Spring Boot 4.1 / Java 21 / PostgreSQL 17
infra/      Terraform (AWS). 콘솔 직접 수정 금지, 이 디렉터리 PR로만 변경
docs/       컨벤션, API 규약, 보안 문서
practice/   Juice Shop 등 보안 도구 실습 환경. 본 서비스 코드가 아니다
.github/    CI, 보안 스캔, CODEOWNERS
```

## 작업 전에 읽을 문서

| 하려는 일 | 읽을 것 |
|---|---|
| 모든 코드 작업 (필수) | [docs/security/secure-coding.md](docs/security/secure-coding.md) |
| 기능 구현, 명세 작성 | [docs/spec/AGENTS.md](docs/spec/AGENTS.md) |
| API 추가·변경 | [docs/conventions/api.md](docs/conventions/api.md) |
| 코드 스타일·네이밍 | [docs/conventions/code-style.md](docs/conventions/code-style.md) |
| 리뷰 작성, 리뷰 반영 | [docs/conventions/code-review.md](docs/conventions/code-review.md) |
| 보안 도구 결과 판별 | [docs/security/tools.md](docs/security/tools.md) |
| 커밋·브랜치·PR | [CONTRIBUTING.md](CONTRIBUTING.md) |

코드 변경으로 권한, DB 스키마, API, 운영 방식이 달라지면 **같은 PR에서 관련 문서도 갱신한다.**

## 영역별 규칙

| 영역 | 규칙 |
|---|---|
| backend | [backend/AGENTS.md](backend/AGENTS.md) |
| frontend | [frontend/AGENTS.md](frontend/AGENTS.md) |

## 작업 순서

1. **이슈부터.** 작업할 이슈를 확인한다. 없으면 이슈 템플릿으로 먼저 만든다. 제목은 커밋과 같은 `type(scope): 한글 설명`이다.
2. **브랜치.** `git fetch origin` 후 `origin/develop`에서 `{type}/{이슈번호}-{영어 kebab-case slug}`로 만든다 (예: `feat/12-notice-list`). 로컬 초안이어도 이슈 번호 없이 브랜치를 만들지 않는다. hotfix만 `origin/main`에서 `hotfix/vX.Y.Z`로 만든다.
3. **커밋.** 메시지는 `type(scope): 한글 설명` (예: `feat(board): 공지 목록 조회 API 구현`). scope는 팀 영역(`auth`, `board`, `gallery`, `infra`)이고 팀에 속하지 않는 변경은 생략한다. 이슈 번호는 쓰지 않는다.
4. **PR.** `develop`으로 보낸다. `main`에는 `release/vX.Y.Z`, `hotfix/vX.Y.Z`만 보낸다. 제목은 커밋과 같은 형식이고, 본문은 PR 템플릿을 채운 뒤 `Closes #이슈번호`를 적는다. 본인을 assignee로 지정한다 (`gh pr create --assignee @me`).
5. **리뷰 반영.** 새 커밋으로 올린다. force push로 덮어쓰지 않는다.

브랜치 이름, PR 제목, `Closes #` 누락은 CI(`Lint PR`)가 막는다. 세부 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)에 있다.

- **PR 작성자는 모든 변경을 본인이 설명할 수 있어야 한다.** AI가 만든 코드도 마찬가지다.
- 인증·파일 업로드·권한 관련 PR은 PL(@sumin0218)의 최종 승인을 받는다.

## 전역 금지

- 시크릿·API 키·비밀번호를 코드, 설정 파일, 테스트, 로그, 커밋 메시지에 넣지 않는다. GitHub Secrets에만 둔다
- `.env`, `terraform.tfvars`, `*.tfstate`를 커밋하지 않는다
- AWS 리소스를 콘솔에서 직접 바꾸지 않는다. Terraform PR로만 바꾼다
- GitHub Actions에서 AWS 액세스 키를 쓰지 않는다. OIDC로만 접근한다
- S3 버킷을 퍼블릭으로 열지 않는다. 파일은 presigned URL로만 주고받는다
- `main`·`develop`에 직접 push하지 않는다
- 권한 규칙이 문서에 없으면 추측해서 구현하지 말고 먼저 물어본다
- 보안 도구 경고를 이유 없이 끄거나 무시 주석을 달지 않는다. 오탐이면 PR 본문에 근거를 적는다

## AI 도구 사용 규칙

- AI 도구에 시크릿, `.env` 내용, AWS 키, 실제 사용자 데이터를 입력하지 않는다
- `practice/`의 취약 앱 코드를 본 서비스에 복사하지 않는다
- 외부 패키지를 추가할 때 이름을 반드시 확인한다 (타이포스쿼팅·존재하지 않는 패키지 제안 주의)

## 코드 리뷰 규칙

리뷰 본문 구조, 결함 심각도 등급, 승인·수정 요청 판정 기준, 스레드 해결 규칙은 [docs/conventions/code-review.md](docs/conventions/code-review.md)에 있다. 리뷰를 쓰기 전에 읽는다.

- 리뷰 글은 한국어로 쓴다. 코드 식별자, 파일 경로, 로그, 오류 메시지는 원문을 유지한다.
- 모든 지적에 심각도 등급을 붙인다. `P1` 사용자나 데이터에 피해가 간다, `P2` 운영에서 문제가 된다, `P3` 아무 일도 안 생긴다. 확인하지 않은 것은 단정하지 말고 `[질문]`을 함께 붙인다.
- 보안 지적에는 관련 OWASP Top 10 2021 / CWE 항목을 함께 적는다.
- 단순 스타일이나 포매팅은 리뷰하지 않는다 (린터 몫).
