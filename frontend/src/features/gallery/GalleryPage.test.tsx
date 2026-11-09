import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { GalleryPage } from './GalleryPage'

// 서버 응답을 흉내 내는 도우미
function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const listBody = {
  content: [
    {
      id: 12,
      title: '10월 정기 세미나',
      eventDate: '2026-10-18',
      coverPhotoUrl: 'https://example.com/12.jpg',
      createdAt: '2026-10-19T09:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

const detailBody = {
  id: 12,
  title: '10월 정기 세미나',
  eventDate: '2026-10-18',
  photos: [{ id: 101, url: 'https://example.com/101.jpg', order: 0 }],
  createdAt: '2026-10-19T09:00:00Z',
}

afterEach(() => {
  vi.unstubAllGlobals()
})

test('목록을 불러와 보여 주고, 앨범을 누르면 상세가 보인다', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(jsonResponse(listBody))
    .mockResolvedValueOnce(jsonResponse(detailBody))
  vi.stubGlobal('fetch', fetchMock)

  render(<GalleryPage />)

  fireEvent.click(
    await screen.findByRole('button', { name: /10월 정기 세미나/ }),
  )

  expect(await screen.findByRole('button', { name: '목록으로' })).toBeDefined()
  expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/albums?page=0&size=20')
  expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/albums/12')
})

test('없는 앨범이면 안내가 보이고 목록은 그대로 남는다', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(listBody))
      .mockResolvedValueOnce(
        jsonResponse(
          {
            status: 404,
            code: 'GALLERY_ALBUM_NOT_FOUND',
            message: '',
            errors: [],
          },
          404,
        ),
      ),
  )

  render(<GalleryPage />)
  fireEvent.click(
    await screen.findByRole('button', { name: /10월 정기 세미나/ }),
  )

  expect((await screen.findByRole('alert')).textContent).toBe(
    '앨범을 찾을 수 없어요.',
  )
  // 목록이 사라지지 않고 다시 누를 수 있다
  expect(screen.getByRole('button', { name: /10월 정기 세미나/ })).toBeDefined()
})

test('목록을 못 불러오면 다시 시도할 수 있다', async () => {
  const fetchMock = vi
    .fn()
    .mockRejectedValueOnce(new TypeError('Failed to fetch')) // 네트워크 끊김
    .mockResolvedValueOnce(jsonResponse(listBody))
  vi.stubGlobal('fetch', fetchMock)

  render(<GalleryPage />)

  expect((await screen.findByRole('alert')).textContent).toBe(
    '앨범 목록을 불러오지 못했어요.',
  )
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))

  expect(
    await screen.findByRole('button', { name: /10월 정기 세미나/ }),
  ).toBeDefined()
  expect(screen.queryByRole('alert')).toBeNull()
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

test('상세를 불러오는 동안 안내가 보이고 다른 앨범을 누를 수 없다', async () => {
  let resolveDetail: (value: Response) => void = () => {}
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(listBody))
      .mockReturnValueOnce(
        new Promise<Response>((resolve) => {
          resolveDetail = resolve // 응답을 테스트가 원할 때 보낸다
        }),
      ),
  )

  render(<GalleryPage />)
  const card = await screen.findByRole('button', { name: /10월 정기 세미나/ })
  fireEvent.click(card)

  expect(await screen.findByText('앨범을 불러오는 중...')).toBeDefined()
  expect((card as HTMLButtonElement).disabled).toBe(true)

  resolveDetail(jsonResponse(detailBody))
  expect(await screen.findByRole('button', { name: '목록으로' })).toBeDefined()
})
