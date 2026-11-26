import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import { AuthProvider } from './AuthProvider'
import { clearSession } from '../lib/session'
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

test('로그인하지 않으면 로그인, 가입하기 버튼이 보인다', async () => {
  // 앱이 시작할 때 refresh를 한 번 부른다. 쿠키가 없으면 401이다.
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({}, 401)))
  renderHeader()

  expect(await screen.findByRole('link', { name: '로그인' })).toBeDefined()
  expect(screen.getByRole('link', { name: '가입하기' })).toBeDefined()
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

test('로그아웃 요청이 실패하면 다시 시도하라고 알린다', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(json(tokenFor('MEMBER')))
    .mockRejectedValueOnce(new TypeError('offline'))
  vi.stubGlobal('fetch', fetchMock)
  renderHeader()

  fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }))

  // 서버가 세션을 폐기하지 못했으면 refresh 쿠키가 남아 다음 사람이 새로고침만으로
  // 앞사람으로 로그인된다. 조용히 로그아웃된 것처럼 보이면 안다 (A07, CWE-613).
  const alert = await screen.findByRole('alert')
  expect(alert.textContent).toContain('로그아웃하지 못했습니다')
  expect(screen.getByRole('button', { name: '다시 시도' })).toBeDefined()
  // 화면의 세션은 비운다. 안내는 로그아웃 버튼이 사라진 뒤에도 보인다.
  expect(screen.getByRole('link', { name: '로그인' })).toBeDefined()
  // 네트워크 실패가 처리되지 않은 Promise 거부로 빠져나가지 않아야 한다.
})

test('다시 시도가 성공하면 안내가 사라진다', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(json(tokenFor('MEMBER')))
    .mockRejectedValueOnce(new TypeError('offline'))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
  vi.stubGlobal('fetch', fetchMock)
  renderHeader()

  fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }))
  fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }))

  // 로그아웃은 refresh 쿠키로만 하므로 세션이 비워진 뒤에도 다시 보낼 수 있다 (4-2-6).
  await waitFor(() => expect(screen.queryByRole('alert')).toBeNull())
  expect(String(fetchMock.mock.calls[2][0])).toBe('/api/v1/auth/logout')
})
