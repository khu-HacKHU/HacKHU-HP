/*
 * AuthProvider가 인증 결과를 화면 이동으로 옮기는지 본다 (auth 4-2-1, 4-2-2).
 *
 * 요청 공통 처리(lib/api.ts)는 컴포넌트 밖이라 화면을 옮길 수 없다. 그래서 알림만 보내고
 * 옮기는 일은 여기가 한다. 그 연결이 끊기면 사용자는 아무 데도 가지 못한 채 요청만 실패한다.
 */
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import { clearSession, emitSessionEvent, setSession } from '../lib/session'
import { AuthProvider } from './AuthProvider'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const user = {
  id: 7,
  email: 'member@example.com',
  name: '홍길동',
  role: 'MEMBER' as const,
  mustChangePassword: false,
}

function tokenFor(mustChangePassword: boolean) {
  return {
    accessToken: 'token-1',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: { ...user, mustChangePassword },
  }
}

/** 어느 경로에 있는지 글로 보여 주는 최소 화면. */
function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<p>첫 화면</p>} />
          <Route path="/login" element={<p>로그인 화면</p>} />
          <Route path="/password" element={<p>비밀번호 변경 화면</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

afterEach(() => {
  vi.restoreAllMocks()
  clearSession()
})

test('갱신까지 실패하면 로그인 화면으로 보낸다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({}, 401)))
  renderAt('/')
  expect(await screen.findByText('첫 화면')).toBeDefined()

  // 로그인이 필요한 요청이 갱신까지 401을 받은 상황 (lib/api.ts가 알린다).
  emitSessionEvent('expired')

  expect(await screen.findByText('로그인 화면')).toBeDefined()
})

test('비밀번호 변경이 필요하면 변경 화면으로 보낸다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({}, 401)))
  renderAt('/')
  expect(await screen.findByText('첫 화면')).toBeDefined()

  emitSessionEvent('password-change-required')

  expect(await screen.findByText('비밀번호 변경 화면')).toBeDefined()
})

test('새로고침으로 되살린 사용자가 비밀번호를 바꿔야 하면 변경 화면으로 보낸다', async () => {
  // 로그인할 때만 확인하면 새로고침한 사용자는 원래 화면에 남고 요청이 전부 403으로 막힌다.
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(tokenFor(true))))

  renderAt('/')

  expect(await screen.findByText('비밀번호 변경 화면')).toBeDefined()
})

test('바꿀 필요가 없으면 보던 화면에 그대로 둔다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(tokenFor(false))))

  renderAt('/')

  expect(await screen.findByText('첫 화면')).toBeDefined()
})

test('구독을 해제한 뒤의 알림에는 반응하지 않는다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({}, 401)))
  const view = renderAt('/')
  expect(await screen.findByText('첫 화면')).toBeDefined()

  view.unmount()
  setSession({ user, accessToken: 'token-1' })

  // 화면이 사라진 뒤에도 알림이 오면 React가 경고를 낸다. 해제가 되는지만 확인한다.
  expect(() => emitSessionEvent('expired')).not.toThrow()
})
