/*
 * 로그인 상태를 담는 곳 (docs/spec/auth/frontend/4-2-QUALITY.md).
 *
 * access 토큰은 이 모듈의 변수에만 있다. localStorage, sessionStorage, 쿠키에 넣지 않는다.
 * 새로고침하면 사라지고, 앱이 시작할 때 refresh 쿠키로 다시 받는다 (CWE-922).
 *
 * React에 기대지 않는 모듈로 둔다. api 호출 래퍼(src/lib/api.ts)가 컴포넌트 밖에서
 * 토큰을 읽고 갱신해야 하기 때문이다.
 *
 * 모든 기능이 로그인한 사용자와 역할을 읽으므로 features/auth가 아니라 여기에 둔다
 * (frontend/AGENTS.md "인증 상태"). 로그인, 가입, 로그아웃 호출은 features/auth/api.ts다.
 */

/** 로그인한 사용자. 서버가 로그인과 refresh 응답으로 준다 (auth 4-2-4). */
export type AuthUser = {
  id: number
  email: string
  name: string
  role: 'PENDING' | 'MEMBER' | 'STAFF' | 'ADMIN'
  mustChangePassword: boolean
}

export type Session = {
  user: AuthUser
  accessToken: string
}

/** 로그인하지 않았으면 null. */
let current: Session | null = null

const listeners = new Set<() => void>()

function notify() {
  for (const listener of listeners) listener()
}

/** 바뀔 때 알림을 받는다. React는 useSyncExternalStore로 쓴다. */
export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** 지금 세션. 참조가 바뀔 때만 다시 그려지도록 같은 객체를 돌려준다. */
export function getSession(): Session | null {
  return current
}

export function setSession(session: Session | null): void {
  current = session
  notify()
}

/**
 * 토큰만 갈아 끼운다. 갱신으로 받은 사용자가 지금 사용자와 다르면 세션을 그 사용자로 바꾼다.
 *
 * <p>다른 탭에서 다른 계정으로 로그인하면 refresh 쿠키가 그 계정의 것으로 덮어써진다. 그래서
 * 갱신 결과의 사용자가 바뀔 수 있다 (auth 4-2-1).
 */
export function replaceSession(user: AuthUser, accessToken: string): void {
  setSession({ user, accessToken })
}

export function clearSession(): void {
  setSession(null)
}

/**
 * 화면을 옮겨야 하는 인증 결과 (auth 4-2-1 "프론트엔드가 분기하는 실패").
 *
 * <ul>
 *   <li>`expired`: 로그인이 필요한 요청이 갱신까지 401을 받았다. 로그인 화면으로 보낸다.
 *   <li>`password-change-required`: 비밀번호를 바꿔야 쓸 수 있는 상태다. 변경 화면으로 보낸다.
 * </ul>
 *
 * <p>요청 공통 처리(lib/api.ts)는 컴포넌트 밖이라 `useNavigate`를 쓸 수 없다. 여기로 알리고
 * `AuthProvider`가 받아서 옮긴다. `window.location`을 직접 바꾸지 않는다
 * (frontend/AGENTS.md "라우팅").
 */
export type SessionEvent = 'expired' | 'password-change-required'

const eventListeners = new Set<(event: SessionEvent) => void>()

export function onSessionEvent(
  listener: (event: SessionEvent) => void,
): () => void {
  eventListeners.add(listener)
  return () => {
    eventListeners.delete(listener)
  }
}

export function emitSessionEvent(event: SessionEvent): void {
  // 듣는 쪽이 구독을 해제해도 이번 알림은 끝까지 돈다.
  for (const listener of [...eventListeners]) listener(event)
}
