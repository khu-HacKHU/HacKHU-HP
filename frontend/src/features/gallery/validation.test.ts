import { describe, expect, test } from 'vitest'
import {
  MAX_PHOTO_SIZE,
  resolveContentType,
  validateEventDate,
  validatePhotos,
  validateTitle,
} from './validation'

// 가짜 파일 만들기. 실제로 10MB를 만들지 않고 size 값만 바꾼다
function makeFile(name: string, type: string, size = 1000): File {
  const file = new File(['x'], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('validatePhotos', () => {
  test('jpg, png, webp 1~20장이면 통과한다', () => {
    const files = [
      makeFile('a.jpg', 'image/jpeg'),
      makeFile('b.png', 'image/png'),
      makeFile('c.webp', 'image/webp'),
    ]
    expect(validatePhotos(files)).toBeNull()
  })

  test('사진이 없으면 막는다', () => {
    expect(validatePhotos([])).toBe('사진을 1장 이상 골라 주세요.')
  })

  test('21장이면 막는다', () => {
    const files = Array.from({ length: 21 }, (_, i) =>
      makeFile(`${i}.jpg`, 'image/jpeg'),
    )
    expect(validatePhotos(files)).toBe('사진은 최대 20장까지 올릴 수 있어요.')
  })

  test('허용하지 않는 형식이면 허용 형식을 안내한다', () => {
    expect(validatePhotos([makeFile('x.svg', 'image/svg+xml')])).toBe(
      'x.svg: jpg, png, webp 형식만 올릴 수 있어요.',
    )
  })

  test('10MB를 넘으면 크기 제한을 안내한다', () => {
    const big = makeFile('big.jpg', 'image/jpeg', MAX_PHOTO_SIZE + 1)
    expect(validatePhotos([big])).toBe(
      'big.jpg: 사진 한 장은 10MB를 넘을 수 없어요.',
    )
  })

  test('정확히 10MB는 통과한다', () => {
    const exact = makeFile('ok.jpg', 'image/jpeg', MAX_PHOTO_SIZE)
    expect(validatePhotos([exact])).toBeNull()
  })
})

describe('resolveContentType', () => {
  test('브라우저가 형식을 비워 주면 확장자로 정한다', () => {
    expect(resolveContentType(makeFile('photo.JPG', ''))).toBe('image/jpeg')
  })

  test('확장자만 jpg로 바꾼 다른 형식은 막는다', () => {
    expect(resolveContentType(makeFile('fake.jpg', 'text/html'))).toBeNull()
  })
})

describe('validateTitle, validateEventDate', () => {
  test('공백만 있는 제목은 막는다', () => {
    expect(validateTitle('   ')).toBe('제목을 입력해 주세요.')
  })

  test('101자 제목은 막는다', () => {
    expect(validateTitle('가'.repeat(101))).toBe(
      '제목은 100자 이하로 입력해 주세요.',
    )
  })

  test('날짜가 비어 있으면 막는다', () => {
    expect(validateEventDate('')).toBe('활동 날짜를 골라 주세요.')
    expect(validateEventDate('2026-10-18')).toBeNull()
  })
})
