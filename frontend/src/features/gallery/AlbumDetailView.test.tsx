import { fireEvent, render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { AlbumDetailView } from './AlbumDetailView'
import type { AlbumDetail } from './types'

const album: AlbumDetail = {
  id: 12,
  title: '10월 정기 세미나',
  eventDate: '2026-10-18',
  photos: [
    { id: 102, url: 'https://example.com/102.jpg', order: 1 },
    { id: 101, url: 'https://example.com/101.jpg', order: 0 },
  ],
  createdAt: '2026-10-19T09:00:00Z',
}

test('제목, 활동 날짜와 사진이 order 순서대로 보인다', () => {
  render(<AlbumDetailView album={album} onBack={() => {}} />)

  expect(
    screen.getByRole('heading', { name: '10월 정기 세미나' }),
  ).toBeDefined()
  expect(screen.getByText('2026-10-18')).toBeDefined()
  const images = screen.getAllByRole('img')
  expect(images).toHaveLength(2)
  expect(images[0].getAttribute('src')).toBe('https://example.com/101.jpg')
  expect(images[1].getAttribute('src')).toBe('https://example.com/102.jpg')
})

test('목록으로 버튼을 누르면 onBack이 불린다', () => {
  const onBack = vi.fn()
  render(<AlbumDetailView album={album} onBack={onBack} />)

  fireEvent.click(screen.getByRole('button', { name: '목록으로' }))

  expect(onBack).toHaveBeenCalled()
})
