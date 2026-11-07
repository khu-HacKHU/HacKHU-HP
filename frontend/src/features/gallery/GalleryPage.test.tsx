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

test('없는 앨범이면 찾을 수 없다는 안내가 보인다', async () => {
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
})
