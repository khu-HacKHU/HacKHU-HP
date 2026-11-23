import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import { clearSession, getSession } from '../../lib/session'
import { LoginPage } from './LoginPage'

function json(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

const tokenBody = {
  accessToken: 'token-1',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    id: 7,
    email: 'member@example.com',
    name: '홍길동',
    role: 'MEMBER',
    mustChangePassword: false,
  },
}

function renderPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  )
}

function fill(email: string, password: string) {
  fireEvent.change(screen.getByLabelText('이메일'), {
    target: { value: email },
  })
  fireEvent.change(screen.getByLabelText('비밀번호'), {
    target: { value: password },
  })
}

afterEach(() => {
  vi.restoreAllMocks()
  clearSession()
})

test('이름표가 있는 입력창 두 개가 보인다', () => {
  renderPage()
  // placeholder를 이름표 대신 쓰지 않는다 (디자인 시스템 6-2).
  expect(screen.getByLabelText('이메일')).toBeDefined()
  expect(screen.getByLabelText('비밀번호')).toBeDefined()
})

test('비어 있으면 버튼을 누를 수 없다', () => {
  renderPage()
  const button = screen.getByRole('button', { name: '로그인' })
  expect(button).toHaveProperty('disabled', true)

  fill('member@example.com', 'Password!234')
  expect(button).toHaveProperty('disabled', false)
})

test('성공하면 세션이 세워진다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(tokenBody)))
  renderPage()

  fill('member@example.com', 'Password!234')
  fireEvent.click(screen.getByRole('button', { name: '로그인' }))

  await waitFor(() => expect(getSession()?.user.id).toBe(7))
})

test('실패하면 안내가 보이고 이유를 가리지 않는다', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValue(
        json({ code: 'AUTH_INVALID_CREDENTIALS', message: '서버 메시지' }, 401),
      ),
  )
  renderPage()

  fill('nobody@example.com', 'wrong')
  fireEvent.click(screen.getByRole('button', { name: '로그인' }))

  const alert = await screen.findByRole('alert')
  expect(alert.textContent).toBe('이메일 또는 비밀번호가 올바르지 않습니다.')
  // 계정이 있는지 알려 주는 문구가 섞이면 안 된다 (auth 4-2-4).
  expect(alert.textContent).not.toContain('이메일이 없')
  expect(alert.textContent).not.toContain('비밀번호가 틀')
  expect(getSession()).toBeNull()
})

test('429면 몇 초 뒤에 다시 시도할지 알려 준다', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValue(
        json({ code: 'TOO_MANY_REQUESTS' }, 429, { 'Retry-After': '30' }),
      ),
  )
  renderPage()

  fill('member@example.com', 'Password!234')
  fireEvent.click(screen.getByRole('button', { name: '로그인' }))

  const alert = await screen.findByRole('alert')
  expect(alert.textContent).toContain('30초')
})

test('연결 실패도 안내로 바꾼다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  renderPage()

  fill('member@example.com', 'Password!234')
  fireEvent.click(screen.getByRole('button', { name: '로그인' }))

  const alert = await screen.findByRole('alert')
  expect(alert.textContent).toContain('연결하지 못했습니다')
})
