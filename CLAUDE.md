# HacKHU-HP

HacKHU 동아리 홈페이지. 인증·권한, 게시판, 갤러리를 만들면서 보안 도구(SAST·SCA·IaC·DAST 등)를 라운드별로 돌려 보고 직접 취약점을 판별·수정하는 교육형 프로젝트다.

## 구조

모노레포. 현재 상태·일정·역할은 [README.md](README.md)가 진입점이다.

```
frontend/   웹 프론트엔드 — 프레임워크 미정
backend/    API 서버 — Spring vs Python 미정 (PL 결정)
infra/      Terraform (AWS). 콘솔 직접 수정 금지, 이 디렉터리 PR로만 변경
docs/       컨벤션, API 규약, 보안 문서
practice/   Juice Shop 등 보안 도구 실습 환경. 본 서비스 코드가 아니다
.github/    CI, 보안 스캔, CODEOWNERS
```

스택이 정해지면 이 표와 아래 "영역별 규칙"을 같은 PR에서 갱신한다.

## 작업 전에 읽을 문서

| 하려는 일 | 읽을 것 |
|---|---|
| 모든 코드 작업 (필수) | [docs/security/secure-coding.md](docs/security/secure-coding.md) |
| API 추가·변경 | [docs/conventions/api.md](docs/conventions/api.md) |
| 코드 스타일·네이밍 | [docs/conventions/code-style.md](docs/conventions/code-style.md) |
| 보안 도구 결과 판별 | [docs/security/tools.md](docs/security/tools.md) |
| 커밋·브랜치·PR | [CONTRIBUTING.md](CONTRIBUTING.md) |

문서에 적힌 규칙을 바꾸는 변경이면 **같은 PR에서 문서도 갱신한다.**

## PR을 올릴 때

- 일반 작업은 `develop`에서 분기해 `develop`으로 PR을 보낸다. `main`에는 `release/vX.Y.Z`만 PR을 보낸다.
- 제목은 `type: 한글 설명` (예: `feat: 공지 목록 조회 API 구현`). scope와 이슈 번호는 쓰지 않는다.
- 본문에 `Closes #이슈번호`를 적고 본인을 assignee로 지정한다 (`gh pr create --assignee @me`).
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

## 영역별 규칙

스택이 정해지기 전이라 공통 규칙만 둔다. 스택 확정 후 `frontend/CLAUDE.md`, `backend/CLAUDE.md`를 만들어 세부 규칙을 적는다.
