import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import { getSession } from '../../lib/session'
import { SignupPage } from './SignupPage'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderPage() {
  return render(
    <MemoryRouter>
      <SignupPage />
    </MemoryRouter>,
  )
}

function fillAll() {
  fireEvent.change(screen.getByLabelText('이메일'), {
    target: { value: 'newbie@example.com' },
  })
  fireEvent.change(screen.getByLabelText('이름'), {
    target: { value: '홍길동' },
  })
  fireEvent.change(screen.getByLabelText('비밀번호'), {
    target: { value: 'Password!234' },
  })
}

afterEach(() => vi.restoreAllMocks())

test('이름표가 있는 입력창 세 개가 보인다', () => {
  renderPage()
  expect(screen.getByLabelText('이메일')).toBeDefined()
  expect(screen.getByLabelText('이름')).toBeDefined()
  expect(screen.getByLabelText('비밀번호')).toBeDefined()
})

test('하나라도 비면 버튼을 누를 수 없다', () => {
  renderPage()
  const button = screen.getByRole('button', { name: '가입' })
  expect(button).toHaveProperty('disabled', true)

  fireEvent.change(screen.getByLabelText('이메일'), {
    target: { value: 'newbie@example.com' },
  })
  expect(button).toHaveProperty('disabled', true)

  fillAll()
  expect(button).toHaveProperty('disabled', false)
})

test('가입을 마치면 승인 대기 안내가 보인다', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      json(
        {
          id: 17,
          email: 'newbie@example.com',
          name: '홍길동',
          role: 'PENDING',
          createdAt: '2026-11-05T09:30:00Z',
        },
        201,
      ),
    ),
  )
  renderPage()

  fillAll()
  fireEvent.click(screen.getByRole('button', { name: '가입' }))

  expect(await screen.findByText(/운영진이 승인하면/)).toBeDefined()
  // 가입은 토큰을 주지 않는다. 로그인되지 않아야 한다 (auth 4-2-3).
  expect(getSession()).toBeNull()
})

test('이미 가입된 이메일이면 안내가 보인다', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(json({ code: 'AUTH_DUPLICATE_EMAIL' }, 409)),
  )
  renderPage()

  fillAll()
  fireEvent.click(screen.getByRole('button', { name: '가입' }))

  const alert = await screen.findByRole('alert')
  expect(alert.textContent).toContain('이미 가입된 이메일')
})

test('서버가 준 항목별 사유를 그 입력창 아래에 보여 준다', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      json(
        {
          code: 'INVALID_INPUT',
          message: '입력값이 올바르지 않습니다.',
          errors: [
            { field: 'password', reason: '비밀번호는 12자 이상이어야 합니다.' },
          ],
        },
        400,
      ),
    ),
  )
  renderPage()

  fillAll()
  fireEvent.click(screen.getByRole('button', { name: '가입' }))

  expect(
    await screen.findByText('비밀번호는 12자 이상이어야 합니다.'),
  ).toBeDefined()
  // 틀린 항목만 표시한다.
  const email = screen.getByLabelText('이메일')
  expect(email.getAttribute('aria-invalid')).toBe('false')
  const password = screen.getByLabelText('비밀번호')
  expect(password.getAttribute('aria-invalid')).toBe('true')
})
