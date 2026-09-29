# backend

API 서버. **스택 미정 (Spring vs Python, PL 결정).**

스택이 정해지면 같은 PR에서 다음을 함께 갱신한다.

- [ ] 이 README — 실행 방법, 환경변수 (`.env.example`)
- [ ] [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) `backend` 잡 — setup-java / setup-python, 빌드·테스트
- [ ] [`.github/workflows/codeql.yml`](../.github/workflows/codeql.yml) — `java-kotlin` 또는 `python` 추가
- [ ] [`.github/dependabot.yml`](../.github/dependabot.yml) — `gradle` 또는 `pip` (`/backend`) 추가
- [ ] [`.github/workflows/security.yml`](../.github/workflows/security.yml) Semgrep 규칙셋 — `p/java`·`p/spring` 또는 `p/python`·`p/django`/`p/flask`
- [ ] [`.github/CODEOWNERS`](../.github/CODEOWNERS) — 인증·파일 업로드·권한 코드 경로에 PL 오너 지정
- [ ] [`docs/conventions/api.md`](../docs/conventions/api.md) — ID 타입, API 명세 도구 확정
- [ ] [`docs/conventions/code-style.md`](../docs/conventions/code-style.md) "스택별 규칙"
- [ ] `backend/CLAUDE.md` — 영역별 규칙 (패키지 구조, 예외 처리, 마이그레이션 등)
