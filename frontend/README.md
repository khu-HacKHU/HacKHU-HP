# frontend

HacKHU 홈페이지 웹 프론트엔드. React 19 / TypeScript / Vite.

작업 규칙은 [AGENTS.md](AGENTS.md)에 있다.

## 준비물

- **Node.js 24.15 이상** ([.nvmrc](.nvmrc)). nvm을 쓰면 `nvm use`

## 실행

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

`/api`로 시작하는 요청은 개발 서버가 로컬 백엔드(`http://localhost:8080`)로 넘긴다 ([vite.config.ts](vite.config.ts)). 백엔드 실행 방법은 [backend/README.md](../backend/README.md)에 있다.

## 포맷·린트·테스트·빌드

```bash
npm run format     # Prettier로 고치기 (CI는 format:check로 검사만 한다)
npm run lint       # oxlint, 경고도 실패로 본다
npm test           # Vitest
npm run build      # 타입 검사 + 빌드
```

CI도 같은 명령을 그대로 돌린다. 로컬에서 통과하면 CI도 통과한다.

## 환경변수

`VITE_`로 시작하는 환경변수는 빌드 결과물에 그대로 들어가 브라우저에서 누구나 볼 수 있다. 키, 토큰, 비밀번호를 넣지 않는다.
