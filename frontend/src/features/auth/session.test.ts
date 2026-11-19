import { afterEach, expect, test } from 'vitest'
import {
  clearSession,
  getSession,
  replaceSession,
  setSession,
  subscribe,
} from './session'

const user = {
  id: 7,
  email: 'member@example.com',
  name: '홍길동',
  role: 'MEMBER' as const,
  mustChangePassword: false,
}

afterEach(() => clearSession())

test('처음에는 로그인하지 않은 상태다', () => {
  expect(getSession()).toBeNull()
})

test('세션을 세우고 비울 수 있다', () => {
  setSession({ user, accessToken: 'token-1' })
  expect(getSession()?.accessToken).toBe('token-1')

  clearSession()
  expect(getSession()).toBeNull()
})

test('토큰을 브라우저 저장소에 넣지 않는다', () => {
  setSession({ user, accessToken: 'secret-token' })

  // 메모리에만 둔다 (CWE-922). localStorage에 두면 XSS로 장기 탈취된다.
  const stored = [
    ...Object.values(localStorage),
    ...Object.values(sessionStorage),
    document.cookie,
  ].join(' ')
  expect(stored).not.toContain('secret-token')
})

test('바뀌면 알림이 온다', () => {
  let calls = 0
  const unsubscribe = subscribe(() => calls++)

  setSession({ user, accessToken: 'token-1' })
  replaceSession(user, 'token-2')
  clearSession()

  expect(calls).toBe(3)

  unsubscribe()
  setSession({ user, accessToken: 'token-3' })
  expect(calls).toBe(3)
})

test('갱신으로 다른 사용자가 오면 그 사용자로 바뀐다', () => {
  setSession({ user, accessToken: 'token-1' })
  const other = { ...user, id: 9, name: '김영희' }

  replaceSession(other, 'token-2')

  expect(getSession()?.user.id).toBe(9)
  expect(getSession()?.accessToken).toBe('token-2')
})
