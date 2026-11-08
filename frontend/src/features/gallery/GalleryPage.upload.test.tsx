import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { GalleryPage } from './GalleryPage'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const emptyList = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
}

afterEach(() => {
  vi.unstubAllGlobals()
})

test('동아리원인지 모르면 올리기 버튼을 보여 주지 않는다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(emptyList)))

  render(<GalleryPage />)

  await screen.findByText(/앨범이 없|아직/)
  expect(screen.queryByRole('button', { name: '앨범 올리기' })).toBeNull()
})

test('동아리원이면 올리기 버튼이 보이고 누르면 올리기 화면으로 간다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(emptyList)))

  render(<GalleryPage canUpload />)

  fireEvent.click(await screen.findByRole('button', { name: '앨범 올리기' }))
  expect(screen.getByRole('heading', { name: '앨범 올리기' })).toBeTruthy()

  fireEvent.click(screen.getByRole('button', { name: '취소' }))
  expect(screen.getByRole('button', { name: '앨범 올리기' })).toBeTruthy()
})
