import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { HomePage } from './HomePage'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function page<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: content.length,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
  }
}

const notice = {
  id: 42,
  title: '11월 정기 세미나 안내',
  author: { id: 7, name: '홍길동' },
  createdAt: '2026-11-07T16:30:00Z', // 한국 시각 11-08 01:30
  updatedAt: '2026-11-07T16:30:00Z',
}

const album = {
  id: 12,
  title: '10월 정기 세미나',
  eventDate: '2026-10-18',
  coverPhotoUrl: 'https://example.com/12.jpg',
  createdAt: '2026-10-19T09:00:00Z',
}

// 요청 주소에 따라 응답을 돌려주는 가짜 서버
function stubServer({
  notices = () => jsonResponse(page([notice])),
  albums = () => jsonResponse(page([album])),
}: {
  notices?: () => Response
  albums?: () => Response
} = {}) {
  const fetchMock = vi.fn(async (url: string) => {
    if (url.startsWith('/api/v1/notices')) return notices()
    if (url.startsWith('/api/v1/albums')) return albums()
    throw new Error(`예상하지 못한 요청: ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const region = (name: string) => screen.getByRole('region', { name })

afterEach(() => {
  vi.unstubAllGlobals()
})

test('방문자에게 소개, 최근 공지, 최근 앨범, 가입·로그인 안내가 모두 보인다', async () => {
  stubServer()
  render(<HomePage onNavigate={vi.fn()} />)

  expect(
    within(region('HacKHU')).getByText('경희대학교 최초의 정보보안 동아리'),
  ).toBeDefined()
  expect(await screen.findByText('11월 정기 세미나 안내')).toBeDefined()
  expect(await screen.findByText('10월 정기 세미나')).toBeDefined()
  expect(screen.getByRole('button', { name: '가입하기' })).toBeDefined()
  expect(screen.getByRole('button', { name: '로그인' })).toBeDefined()
})

test('최근 공지는 5개, 최근 앨범은 4개를 요청한다', async () => {
  const fetchMock = stubServer()
  render(<HomePage onNavigate={vi.fn()} />)
  await screen.findByText('10월 정기 세미나')

  const urls = fetchMock.mock.calls.map(([url]) => url)
  expect(urls).toContain('/api/v1/notices?page=0&size=5')
  expect(urls).toContain('/api/v1/albums?page=0&size=4')
})

test('공지 작성일은 한국 날짜로 보인다', async () => {
  stubServer()
  render(<HomePage onNavigate={vi.fn()} />)

  expect(await screen.findByText('2026-11-08')).toBeDefined()
})

test('공지가 없으면 그 영역에 빈 목록 안내가 보인다', async () => {
  stubServer({ notices: () => jsonResponse(page([])) })
  render(<HomePage onNavigate={vi.fn()} />)

  expect(
    await within(region('최근 공지')).findByText('아직 올라온 공지가 없어요.'),
  ).toBeDefined()
})

test('앨범이 없으면 그 영역에 빈 목록 안내가 보인다', async () => {
  stubServer({ albums: () => jsonResponse(page([])) })
  render(<HomePage onNavigate={vi.fn()} />)

  expect(
    await within(region('최근 앨범')).findByText('아직 올라온 앨범이 없어요.'),
  ).toBeDefined()
})

test('공지를 못 불러와도 그 영역에만 안내가 보이고 앨범은 그대로 보인다', async () => {
  stubServer({ notices: () => jsonResponse({}, 500) })
  render(<HomePage onNavigate={vi.fn()} />)

  expect(
    (await within(region('최근 공지')).findByRole('alert')).textContent,
  ).toBe('공지를 불러오지 못했어요.')
  expect(await screen.findByText('10월 정기 세미나')).toBeDefined()
  expect(within(region('최근 앨범')).queryByRole('alert')).toBeNull()
})

test('앨범을 못 불러와도 그 영역에만 안내가 보이고 공지는 그대로 보인다', async () => {
  stubServer({ albums: () => jsonResponse({}, 500) })
  render(<HomePage onNavigate={vi.fn()} />)

  expect(
    (await within(region('최근 앨범')).findByRole('alert')).textContent,
  ).toBe('앨범을 불러오지 못했어요.')
  expect(await screen.findByText('11월 정기 세미나 안내')).toBeDefined()
})

test('승인 대기 회원에게는 승인을 기다린다는 안내가 보이고 가입·로그인은 안 보인다', async () => {
  stubServer()
  render(<HomePage role="PENDING" onNavigate={vi.fn()} />)

  expect(screen.getByRole('status').textContent).toContain(
    '가입 승인을 기다리고 있어요',
  )
  expect(screen.queryByRole('button', { name: '가입하기' })).toBeNull()
  expect(screen.queryByRole('button', { name: '로그인' })).toBeNull()
  await screen.findByText('10월 정기 세미나')
})

test('동아리원에게는 가입·로그인 링크가 보이지 않는다', async () => {
  stubServer()
  render(<HomePage role="MEMBER" onNavigate={vi.fn()} />)

  expect(screen.queryByRole('button', { name: '가입하기' })).toBeNull()
  expect(screen.queryByRole('button', { name: '로그인' })).toBeNull()
  expect(screen.queryByRole('status')).toBeNull()
  await screen.findByText('10월 정기 세미나')
})

test('각 항목과 링크를 누르면 해당 화면으로 이동을 요청한다', async () => {
  stubServer()
  const onNavigate = vi.fn()
  render(<HomePage onNavigate={onNavigate} />)

  fireEvent.click(await screen.findByText('11월 정기 세미나 안내'))
  fireEvent.click(await screen.findByText('10월 정기 세미나'))
  fireEvent.click(screen.getByRole('button', { name: '동아리 소개 보기' }))
  fireEvent.click(screen.getByRole('button', { name: '공지 전체 보기' }))
  fireEvent.click(screen.getByRole('button', { name: '갤러리 가기' }))
  fireEvent.click(screen.getByRole('button', { name: '가입하기' }))
  fireEvent.click(screen.getByRole('button', { name: '로그인' }))

  expect(onNavigate.mock.calls.map(([d]) => d)).toEqual([
    { to: 'notice', id: 42 },
    { to: 'album', id: 12 },
    { to: 'intro' },
    { to: 'notices' },
    { to: 'gallery' },
    { to: 'signup' },
    { to: 'login' },
  ])
})
