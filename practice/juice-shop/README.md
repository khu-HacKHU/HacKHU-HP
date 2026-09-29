# Juice Shop 보안 도구 실습

S1 1주차 실습. [OWASP Juice Shop](https://owasp.org/www-project-juice-shop/)은 OWASP Top 10 취약점을 일부러 넣어 둔 웹앱이다. 1R에서 쓸 도구(Semgrep, gitleaks, Checkov)를 실제 취약 코드에 돌려 보고, **도구가 찾은 것과 못 찾은 것**을 직접 확인한다.

> ⚠️ 실습 환경은 본인 PC에서만 띄운다. 여기서 배운 공격 기법은 이 앱과 우리 프로젝트의 S4 Exploit 범위 안에서만 쓴다.

## 준비물

- Docker Desktop
- Git

## 1. 앱 띄우기

```bash
cd practice/juice-shop
docker compose up -d
```

브라우저에서 http://localhost:3000 을 연다. 끌 때는 `docker compose down`.

## 2. 소스 받기

도구를 돌릴 소스 코드를 받는다. `src/`는 `.gitignore`로 제외되어 있어 커밋되지 않는다.

```bash
git clone --depth 1 --branch v20.2.0 https://github.com/juice-shop/juice-shop.git src
```

## 3. 도구 실습

모두 `practice/juice-shop`에서 실행한다. Windows PowerShell에서는 `$PWD`를 `${PWD}`로 쓴다.

### SAST — Semgrep

```bash
docker run --rm -v "$PWD/src:/src" -w /src semgrep/semgrep:1.178.0 \
  semgrep scan --metrics=off --config p/owasp-top-ten --config p/javascript
```

- `routes/login.ts`에서 SQL 인젝션이 잡히는가?
- 잡힌 경고 중 오탐은 어떤 것인가?

### 시크릿 — gitleaks

```bash
docker run --rm -v "$PWD/src:/repo" -w /repo ghcr.io/gitleaks/gitleaks:v8.30.1 dir --redact --verbose .
```

- 하드코딩된 키가 잡히는가? 그중 실제로 위험한 것은?

### IaC·컨테이너 설정 — Checkov

```bash
docker run --rm -v "$PWD/src:/src" -w /src bridgecrew/checkov -d . --framework dockerfile,github_actions,kubernetes --compact
```

### DAST 맛보기 — OWASP ZAP (2R 예습, 선택)

```bash
docker compose --profile dast run --rm zap
```

`reports/zap-report.html`을 연다.

## 4. 직접 공격해 보기

앱의 스코어보드(http://localhost:3000/#/score-board)에서 ⭐1~2 난이도 문제를 풀어 본다. 추천:

| 문제 | 관련 취약점 | 우리 프로젝트에서 대응하는 곳 |
|---|---|---|
| Login Admin | SQL 인젝션 | 인증 팀 로그인 API |
| DOM XSS | XSS | 게시판 글 출력 |
| View Basket | IDOR (인가 누락) | 게시판·갤러리 수정·삭제 권한 체크 |
| Upload Type | 파일 업로드 검증 | 갤러리 presigned URL |
| Error Handling | 에러 메시지 노출 | API 공통 에러 응답 |

## 5. 정리해서 공유하기

단톡방에 한 줄씩 공유한다.

- 도구가 찾은 취약점 중 **실제로 공격에 성공한 것** 하나
- 도구가 **못 찾았는데** 스코어보드로 찾은 것 하나

두 번째가 이 실습의 핵심이다. 도구가 못 잡는 것(주로 인가 로직)은 코드 리뷰와 S4 Exploit에서 사람이 찾아야 한다.
