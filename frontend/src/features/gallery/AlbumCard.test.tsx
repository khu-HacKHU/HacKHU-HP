import { fireEvent, render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { AlbumCard } from './AlbumCard'
import type { AlbumSummary } from './types'

// 서버 대신 쓰는 가짜 앨범 데이터
const album: AlbumSummary = {
  id: 12,
  title: '10월 정기 세미나',
  eventDate: '2026-10-18',
  coverPhotoUrl: 'https://example.com/cover.jpg',
  createdAt: '2026-10-19T09:00:00Z',
}

test('제목, 활동 날짜, 대표 사진이 보인다', () => {
  render(<AlbumCard album={album} onSelect={() => {}} />)

  expect(screen.getByText('10월 정기 세미나')).toBeDefined()
  expect(screen.getByText('2026-10-18')).toBeDefined()
  expect(
    screen.getByRole('img', { name: '10월 정기 세미나 대표 사진' }),
  ).toBeDefined()
})

test('카드를 누르면 앨범 id가 전달된다', () => {
  const onSelect = vi.fn() // 불렸는지 기록하는 가짜 함수
  render(<AlbumCard album={album} onSelect={onSelect} />)

  fireEvent.click(screen.getByRole('button'))

  expect(onSelect).toHaveBeenCalledWith(12)
})
