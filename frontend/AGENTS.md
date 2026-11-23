# frontend 규칙

루트 [AGENTS.md](../AGENTS.md)의 전역 규칙과 [시큐어 코딩 규칙](../docs/security/secure-coding.md)을 먼저 따른다. 여기는 프론트엔드에만 해당하는 규칙이다.

## 스택

React 19 / TypeScript / Vite / oxlint / Prettier / Vitest

## 코드 규칙

- `dangerouslySetInnerHTML`을 쓰지 않는다. 게시글 같은 사용자 입력은 React 기본 이스케이프로만 출력한다 (CWE-79)
- 권한에 따라 버튼을 숨기는 것은 화면 편의일 뿐이다. 권한 검사는 서버가 한다
- `VITE_` 환경변수는 브라우저에 그대로 노출된다. 키, 토큰, 비밀번호를 넣지 않는다
- 백엔드 호출 경로는 `/api/v1`로 시작한다 ([API 규약](../docs/conventions/api.md)). 개발 중에는 Vite 프록시가 로컬 백엔드로 넘긴다

## 폴더 구조

```
src/
  styles/tokens.css       디자인 시스템 값. 색·간격·모서리를 여기서만 정한다
  lib/                    기능에 속하지 않는 공용 코드 (api 호출 래퍼, 로그인 상태 등)
  components/             두 기능 이상이 같이 쓰는 컴포넌트
  features/<기능>/         한 기능만 쓰는 화면, 컴포넌트, api, 타입
```

- `features/<기능>/`의 기능 이름은 명세 폴더와 같게 쓴다 (`auth`, `board`, `gallery`, `home`).
- **공용 컴포넌트는 `src/components/`에 둔다.** 처음 만드는 팀이 거기 두고 다른 팀은 가져다 쓴다 ([UI 디자인 시스템](../docs/spec/UI-DESIGN-SYSTEM.md) 6절). 한 기능만 쓰는 컴포넌트를 미리 올리지 않는다. 두 번째 기능이 쓰게 될 때 옮긴다.
- 다른 기능의 `features/` 안쪽을 직접 가져오지 않는다. 필요하면 `components/`나 `lib/`로 올린다.

## 라우팅

`react-router`의 `BrowserRouter`를 쓴다. 해시 주소(`/#/...`)를 쓰지 않는다. 공지와 갤러리가 공개라서 링크를 공유하고 검색에 노출되어야 한다 ([docs/spec/DECISIONS.md](../docs/spec/DECISIONS.md) D7).

- 경로 정의는 `src/App.tsx` 한곳에 모은다. 기능별로 흩지 않는다.
- 화면 이동은 `<Link>`와 `useNavigate`로 한다. `window.location`을 직접 바꾸지 않는다.
- 배포에서 없는 경로를 첫 화면으로 돌리는 처리가 필요하다. 프론트엔드 경로에만 걸어야 한다 ([DEPLOYMENT.md](../docs/spec/DEPLOYMENT.md) 1-2).

## 상태 관리

상태 관리 라이브러리를 쓰지 않는다. 화면 6개 규모에서는 React 기본 기능으로 충분하다.

| 상태                                      | 두는 곳                                       |
| ----------------------------------------- | --------------------------------------------- |
| 화면 하나만 쓰는 상태 (입력값, 열림 여부) | `useState`                                    |
| 여러 화면이 쓰는 상태 (로그인한 사용자)   | Context                                       |
| 서버에서 받은 데이터                      | 그 화면에서 `useState`와 `useEffect`로 받는다 |

같은 데이터를 여러 화면이 반복해서 받아 문제가 되면 그때 캐시 방법을 정한다. 미리 넣지 않는다.

## API 호출

- 호출 함수는 `features/<기능>/api.ts`에 모은다. 컴포넌트에서 `fetch`를 직접 부르지 않는다.
- 공통 처리(`credentials`, access 토큰 헤더, 401 뒤 갱신, 오류 변환)는 `src/lib/api.ts`의 `request`가 한다. 기능별 `api.ts`는 그것을 쓴다.
- 경로는 `/api/v1`로 시작한다 ([API 규약](../docs/conventions/api.md)).
- **모든 요청에 `credentials: 'include'`를 붙인다.** 쿠키를 다루는 API만 골라 붙이면 빠뜨리기 쉽다 ([auth 4-2-1](../docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md)).

## 스타일링

**CSS Modules와 CSS 변수를 쓴다.** 유틸리티 클래스 프레임워크를 쓰지 않는다.

- 디자인 시스템 값은 `src/styles/tokens.css`에 CSS 변수로 한 번만 정의한다.
- 컴포넌트 스타일은 같은 폴더의 `<컴포넌트>.module.css`에 둔다.
- **`*.module.css`에 색, 간격, 모서리 값을 직접 적지 않는다.** `var(--panel)`, `var(--space-4)`처럼 변수로 가져온다. 값을 바꿀 일이 생기면 토큰 한곳만 고친다.
- 디자인 시스템에 없는 값이 필요하면 화면에 넣지 말고 [UI 디자인 시스템](../docs/spec/UI-DESIGN-SYSTEM.md)에 먼저 추가한다.
- 인라인 `style`은 값이 실행 중에 정해질 때만 쓴다 (예: 진행률 너비).

## 인증 상태

access 토큰은 **메모리에만** 둔다. `localStorage`, `sessionStorage`, 쿠키에 넣지 않는다 (CWE-922). 보관 방법과 새로고침 뒤 복구는 [auth/frontend/4-2-QUALITY.md](../docs/spec/auth/frontend/4-2-QUALITY.md)에서 정한다.

**로그인 상태는 `src/lib/`에 있다.** 모든 기능이 로그인한 사용자와 역할을 읽으므로 `features/auth/` 안에 두지 않는다. 거기 두면 다른 팀이 쓸 때마다 "다른 기능의 `features/` 안쪽을 가져오지 않는다"를 어기게 된다.

| 무엇                                   | 어디                              |
| -------------------------------------- | --------------------------------- |
| 지금 로그인한 사용자와 역할 (컴포넌트) | `lib/useAuth.ts`의 `useAuth()`    |
| 토큰과 사용자 (컴포넌트 밖)            | `lib/session.ts`의 `getSession()` |
| 로그인 상태를 화면에 연결하는 Provider | `components/AuthProvider.tsx`     |
| 로그인, 가입, 로그아웃 **호출**        | `features/auth/api.ts`            |

- 권한에 따라 화면을 바꿀 때는 `useAuth().session?.user.role`을 본다. 역할 검사는 서버가 하고, 화면은 편의일 뿐이다.
- 토큰을 컴포넌트에서 직접 읽어 `fetch`에 붙이지 않는다. `lib/api.ts`의 `request`가 붙인다.

## 테스트

- 컴포넌트 테스트는 `*.test.tsx`로 같은 폴더에 둔다 (Vitest + Testing Library)
- 커밋 전에 `npm run format`, `npm run lint`, `npm test`, `npm run build`를 돌린다
