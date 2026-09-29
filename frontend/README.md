# frontend

웹 프론트엔드. **프레임워크 미정.**

프레임워크가 정해지면 같은 PR에서 다음을 함께 갱신한다.

- [ ] 이 README — 실행 방법, 환경변수 (`.env.example`)
- [ ] [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) `frontend` 잡 — setup-node, 린트·테스트·빌드
- [ ] [`.github/workflows/codeql.yml`](../.github/workflows/codeql.yml) — `javascript-typescript` 추가
- [ ] [`.github/dependabot.yml`](../.github/dependabot.yml) — `npm` (`/frontend`) 추가
- [ ] [`.github/workflows/security.yml`](../.github/workflows/security.yml) Semgrep 규칙셋 — 필요 시 `p/typescript`, `p/react` 등
- [ ] [`docs/conventions/code-style.md`](../docs/conventions/code-style.md) "스택별 규칙"
- [ ] `frontend/CLAUDE.md` — 영역별 규칙 (API 호출 위치, 상태관리 등)
