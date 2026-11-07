export const MAX_PHOTOS = 20
export const MAX_PHOTO_SIZE = 10 * 1024 * 1024 // 10MB (백엔드와 같은 값)
export const MAX_TITLE_LENGTH = 100

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

// 확장자로 형식을 추정한다. 일부 브라우저는 file.type을 빈 값으로 줄 때가 있다.
const TYPE_BY_EXTENSION: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
}

// 파일의 MIME 형식을 정한다. 허용 형식이 아니면 null
export function resolveContentType(file: File): string | null {
  if (ALLOWED_TYPES.includes(file.type)) return file.type
  if (file.type !== '') return null
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  return TYPE_BY_EXTENSION[extension] ?? null
}

// 사진 목록 검사. 문제가 없으면 null, 있으면 안내 문구를 돌려준다
export function validatePhotos(files: File[]): string | null {
  if (files.length === 0) return '사진을 1장 이상 골라 주세요.'
  if (files.length > MAX_PHOTOS) {
    return `사진은 최대 ${MAX_PHOTOS}장까지 올릴 수 있어요.`
  }
  for (const file of files) {
    if (resolveContentType(file) === null) {
      return `${file.name}: jpg, png, webp 형식만 올릴 수 있어요.`
    }
    if (file.size > MAX_PHOTO_SIZE) {
      return `${file.name}: 사진 한 장은 10MB를 넘을 수 없어요.`
    }
  }
  return null
}

// 제목 검사 (1~100자, 공백만 있으면 안 됨)
export function validateTitle(title: string): string | null {
  if (title.trim() === '') return '제목을 입력해 주세요.'
  if (title.length > MAX_TITLE_LENGTH) {
    return `제목은 ${MAX_TITLE_LENGTH}자 이하로 입력해 주세요.`
  }
  return null
}

// 활동 날짜 검사 (비어 있으면 안 됨)
export function validateEventDate(eventDate: string): string | null {
  if (eventDate === '') return '활동 날짜를 골라 주세요.'
  return null
}
