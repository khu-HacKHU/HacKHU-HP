import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { AlbumList } from './AlbumList'
import type { AlbumSummary } from './types'

const albums: AlbumSummary[] = [
  {
    id: 12,
    title: '10월 정기 세미나',
    eventDate: '2026-10-18',
    coverPhotoUrl: 'https://example.com/12.jpg',
    createdAt: '2026-10-19T09:00:00Z',
  },
  {
    id: 7,
    title: '9월 MT',
    eventDate: '2026-09-20',
    coverPhotoUrl: 'https://example.com/7.jpg',
    createdAt: '2026-09-21T09:00:00Z',
  },
]

test('앨범이 없으면 빈 목록 안내가 보인다', () => {
  render(<AlbumList albums={[]} onSelect={() => {}} />)

  expect(screen.getByText('아직 올라온 앨범이 없어요.')).toBeDefined()
})

test('앨범을 받은 순서대로 보여 준다', () => {
  render(<AlbumList albums={albums} onSelect={() => {}} />)

  const items = screen.getAllByRole('listitem') // <li>를 전부 찾기
  expect(items).toHaveLength(2)
  expect(items[0].textContent).toContain('10월 정기 세미나')
  expect(items[1].textContent).toContain('9월 MT')
})
