import { fireEvent, render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { AlbumDetailView } from './AlbumDetailView'
import type { AlbumDetail } from './types'

const album: AlbumDetail = {
  id: 12,
  title: '10월 정기 세미나',
  eventDate: '2026-10-18',
  canDelete: false,
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

test('canDelete가 false면 삭제 버튼이 보이지 않는다', () => {
  render(<AlbumDetailView album={album} onBack={() => {}} />)

  expect(screen.queryByRole('button', { name: '앨범 삭제' })).toBeNull()
})

test('삭제는 한 번 더 확인한 뒤에 불린다', () => {
  const onDelete = vi.fn()
  render(
    <AlbumDetailView
      album={{ ...album, canDelete: true }}
      onBack={() => {}}
      onDelete={onDelete}
    />,
  )

  fireEvent.click(screen.getByRole('button', { name: '앨범 삭제' }))
  expect(onDelete).not.toHaveBeenCalled() // 아직 확인을 안 했다

  fireEvent.click(screen.getByRole('button', { name: '삭제' }))
  expect(onDelete).toHaveBeenCalled()
})

test('확인에서 취소하면 삭제되지 않고 처음 상태로 돌아간다', () => {
  const onDelete = vi.fn()
  render(
    <AlbumDetailView
      album={{ ...album, canDelete: true }}
      onBack={() => {}}
      onDelete={onDelete}
    />,
  )

  fireEvent.click(screen.getByRole('button', { name: '앨범 삭제' }))
  fireEvent.click(screen.getByRole('button', { name: '취소' }))

  expect(onDelete).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: '앨범 삭제' })).toBeDefined()
})

test('삭제하는 중에는 확인 버튼을 누를 수 없다', () => {
  render(
    <AlbumDetailView
      album={{ ...album, canDelete: true }}
      onBack={() => {}}
      onDelete={() => {}}
      deleting
    />,
  )

  fireEvent.click(screen.getByRole('button', { name: '앨범 삭제' }))

  expect(
    (screen.getByRole('button', { name: '삭제' }) as HTMLButtonElement)
      .disabled,
  ).toBe(true)
})

test('확인 단계로 바뀌면 삭제 버튼에 포커스가 간다', () => {
  render(
    <AlbumDetailView
      album={{ ...album, canDelete: true }}
      onBack={() => {}}
      onDelete={() => {}}
    />,
  )

  fireEvent.click(screen.getByRole('button', { name: '앨범 삭제' }))

  expect(document.activeElement).toBe(
    screen.getByRole('button', { name: '삭제' }),
  )
})
