import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import { AuthProvider } from '../features/auth/AuthProvider'
import { clearSession } from '../features/auth/session'
import { Header } from './Header'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function tokenFor(role: 'PENDING' | 'MEMBER' | 'STAFF') {
  return {
    accessToken: 'token-1',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: 7,
      email: 'member@example.com',
      name: '홍길동',
      role,
      mustChangePassword: false,
    },
  }
}

function renderHeader() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Header />
      </AuthProvider>
    </MemoryRouter>,
  )
}

afterEach(() => {
  vi.restoreAllMocks()
  clearSession()
})

test('로그인하지 않으면 로그인, 가입 버튼이 보인다', async () => {
  // 앱이 시작할 때 refresh를 한 번 부른다. 쿠키가 없으면 401이다.
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({}, 401)))
  renderHeader()

  expect(await screen.findByRole('link', { name: '로그인' })).toBeDefined()
  expect(screen.getByRole('link', { name: '가입' })).toBeDefined()
  expect(screen.queryByRole('button', { name: '로그아웃' })).toBeNull()
})

test('새로고침 뒤 refresh 쿠키로 로그인 상태가 되살아난다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(tokenFor('MEMBER'))))
  renderHeader()

  // access 토큰은 메모리에만 있어 새로고침하면 사라진다. 쿠키로 다시 받는다.
  expect(await screen.findByText('홍길동')).toBeDefined()
  expect(screen.getByRole('button', { name: '로그아웃' })).toBeDefined()
  expect(screen.queryByRole('link', { name: '로그인' })).toBeNull()
})

test('승인 대기 회원은 역할 배지가 승인 대기로 보인다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(tokenFor('PENDING'))))
  renderHeader()

  expect(await screen.findByText('승인 대기')).toBeDefined()
})

test('운영진은 역할 배지가 운영진으로 보인다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(tokenFor('STAFF'))))
  renderHeader()

  expect(await screen.findByText('운영진')).toBeDefined()
})

test('로그아웃을 누르면 세션이 비워진다', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(json(tokenFor('MEMBER')))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
  vi.stubGlobal('fetch', fetchMock)
  renderHeader()

  fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }))

  await waitFor(() =>
    expect(screen.getByRole('link', { name: '로그인' })).toBeDefined(),
  )
  expect(String(fetchMock.mock.calls[1][0])).toBe('/api/v1/auth/logout')
})

test('로그아웃 요청이 실패해도 화면은 로그아웃된다', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(json(tokenFor('MEMBER')))
    .mockRejectedValueOnce(new TypeError('offline'))
  vi.stubGlobal('fetch', fetchMock)
  renderHeader()

  fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }))

  // 사용자가 로그아웃을 눌렀는데 로그인 상태로 남아 있는 쪽이 더 나쁘다.
  await waitFor(() =>
    expect(screen.getByRole('link', { name: '로그인' })).toBeDefined(),
  )
  // 네트워크 실패가 처리되지 않은 Promise 거부로 빠져나가지 않아야 한다.
  // onLogout이 잡지 않으면 테스트는 통과하지만 vitest가 프로세스를 실패로 끝낸다.
})
