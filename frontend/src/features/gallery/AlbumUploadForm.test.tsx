import { fireEvent, render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { AlbumUploadForm } from './AlbumUploadForm'

function makeFile(name: string, type: string, size = 1000): File {
  const file = new File(['x'], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

// 폼 채우기 도우미
function fillForm(title: string, eventDate: string, photos: File[]) {
  fireEvent.change(screen.getByLabelText('제목'), {
    target: { value: title },
  })
  fireEvent.change(screen.getByLabelText('활동 날짜'), {
    target: { value: eventDate },
  })
  fireEvent.change(screen.getByLabelText(/사진/), {
    target: { files: photos },
  })
}

test('입력이 올바르면 onSubmit에 값을 넘긴다', () => {
  const onSubmit = vi.fn()
  render(<AlbumUploadForm onSubmit={onSubmit} />)
  const photo = makeFile('a.jpg', 'image/jpeg')

  fillForm(' 10월 정기 세미나 ', '2026-10-18', [photo])
  fireEvent.click(screen.getByRole('button', { name: '올리기' }))

  expect(onSubmit).toHaveBeenCalledWith({
    title: '10월 정기 세미나',
    eventDate: '2026-10-18',
    photos: [photo],
  })
  expect(screen.queryByRole('alert')).toBeNull()
})

test('허용하지 않는 형식이면 올리지 않고 이유를 안내한다', () => {
  const onSubmit = vi.fn()
  render(<AlbumUploadForm onSubmit={onSubmit} />)

  fillForm('세미나', '2026-10-18', [makeFile('x.svg', 'image/svg+xml')])
  fireEvent.click(screen.getByRole('button', { name: '올리기' }))

  expect(onSubmit).not.toHaveBeenCalled()
  expect(screen.getByRole('alert').textContent).toBe(
    'x.svg: jpg, png, webp 형식만 올릴 수 있어요.',
  )
})

test('사진 없이 올리면 막는다', () => {
  const onSubmit = vi.fn()
  render(<AlbumUploadForm onSubmit={onSubmit} />)

  fillForm('세미나', '2026-10-18', [])
  fireEvent.click(screen.getByRole('button', { name: '올리기' }))

  expect(onSubmit).not.toHaveBeenCalled()
  expect(screen.getByRole('alert').textContent).toBe(
    '사진을 1장 이상 골라 주세요.',
  )
})
