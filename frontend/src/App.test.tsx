import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import App from './App.tsx'
import { clearSession } from './lib/session'

afterEach(() => {
  vi.restoreAllMocks()
  clearSession()
})

// App이 뜰 때 AuthProvider가 refresh를 한 번 부르고, 첫 화면의 갤러리가 목록을 받는다.
// role을 주면 그 역할로 로그인해 있던 것처럼 refresh가 성공한다.
function stubFetch(role?: 'PENDING' | 'MEMBER' | 'STAFF' | 'ADMIN') {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      if (String(url) === '/api/v1/auth/refresh') {
        if (role === undefined) {
          return Promise.resolve(new Response(null, { status: 401 }))
        }
        return Promise.resolve(
          new Response(
            JSON.stringify({
              accessToken: 'test-token',
              tokenType: 'Bearer',
              expiresIn: 900,
              user: {
                id: 1,
                email: 'user@example.com',
                name: '사용자',
                role,
                mustChangePassword: false,
              },
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        )
      }
      return Promise.resolve(
        new Response(
          JSON.stringify({
            content: [],
            page: 0,
            size: 20,
            totalElements: 0,
            totalPages: 0,
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
    }),
  )
}

test('첫 화면에 동아리 이름이 보인다', async () => {
  stubFetch()
  render(<App />)
  // 헤더의 로고는 첫 화면으로 가는 링크다 (디자인 시스템 7절).
  expect(await screen.findByRole('link', { name: 'HacKHU' })).toBeDefined()
})

test('로그인하지 않으면 헤더에 로그인, 가입이 보인다', async () => {
  stubFetch()
  render(<App />)
  expect(await screen.findByRole('link', { name: '로그인' })).toBeDefined()
  expect(screen.getByRole('link', { name: '가입' })).toBeDefined()
})

test('동아리원으로 로그인하면 갤러리에 앨범 올리기 버튼이 보인다', async () => {
  stubFetch('MEMBER')
  render(<App />)
  expect(
    await screen.findByRole('button', { name: '앨범 올리기' }),
  ).toBeDefined()
})

test('승인 대기 회원에게는 올리기 버튼이 없다', async () => {
  stubFetch('PENDING')
  render(<App />)
  // 로그인 상태 복구가 끝나 헤더에 역할이 보인 뒤에 확인한다
  await screen.findByText('승인 대기')
  await screen.findByText(/앨범이 없|아직/)
  expect(screen.queryByRole('button', { name: '앨범 올리기' })).toBeNull()
})

test('로그인하지 않은 방문자에게는 올리기 버튼이 없다', async () => {
  stubFetch()
  render(<App />)
  await screen.findByRole('link', { name: '로그인' })
  await screen.findByText(/앨범이 없|아직/)
  expect(screen.queryByRole('button', { name: '앨범 올리기' })).toBeNull()
})
