/*
 * 로그인 상태를 담는 곳 (docs/spec/auth/frontend/4-2-QUALITY.md).
 *
 * access 토큰은 이 모듈의 변수에만 있다. localStorage, sessionStorage, 쿠키에 넣지 않는다.
 * 새로고침하면 사라지고, 앱이 시작할 때 refresh 쿠키로 다시 받는다 (CWE-922).
 *
 * React에 기대지 않는 모듈로 둔다. api 호출 래퍼(src/lib/api.ts)가 컴포넌트 밖에서
 * 토큰을 읽고 갱신해야 하기 때문이다.
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
