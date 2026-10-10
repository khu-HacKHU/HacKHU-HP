# frontend 규칙

루트 [AGENTS.md](../AGENTS.md)의 전역 규칙과 [시큐어 코딩 규칙](../docs/security/secure-coding.md)을 먼저 따른다. 여기는 프론트엔드에만 해당하는 규칙이다.

## 스택

React 19 / TypeScript / Vite / oxlint / Vitest

## 코드 규칙

- `dangerouslySetInnerHTML`을 쓰지 않는다. 게시글 같은 사용자 입력은 React 기본 이스케이프로만 출력한다 (CWE-79)
- 권한에 따라 버튼을 숨기는 것은 화면 편의일 뿐이다. 권한 검사는 서버가 한다
- `VITE_` 환경변수는 브라우저에 그대로 노출된다. 키, 토큰, 비밀번호를 넣지 않는다
- 백엔드 호출 경로는 `/api/v1`로 시작한다 ([API 규약](../docs/conventions/api.md)). 개발 중에는 Vite 프록시가 로컬 백엔드로 넘긴다

## 아직 정하지 않은 것

API 호출 위치, 상태 관리, 라우팅, 스타일링 방식은 아직 정하지 않았다. 필요해지면 추측해서 넣지 말고 PL과 정한 뒤 이 문서에 적는다.

## 테스트

- 컴포넌트 테스트는 `*.test.tsx`로 같은 폴더에 둔다 (Vitest + Testing Library)
- 커밋 전에 `npm run lint`, `npm test`, `npm run build`를 돌린다
