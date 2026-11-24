import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import App from './App.tsx'
import { clearSession } from './lib/session'

afterEach(() => {
  vi.restoreAllMocks()
  clearSession()
  window.history.pushState({}, '', '/')
})

// App이 뜰 때 AuthProvider가 refresh를 한 번 부르고, 첫 화면의 갤러리가 목록을 받는다.
function stubFetch() {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      if (String(url) === '/api/v1/auth/refresh') {
        return Promise.resolve(new Response(null, { status: 401 }))
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

test('/about 주소로 들어오면 동아리 소개 화면이 보인다', async () => {
  stubFetch()
  window.history.pushState({}, '', '/about')
  render(<App />)
  expect(
    await screen.findByRole('heading', { name: 'HacKHU 소개' }),
  ).toBeDefined()
})
