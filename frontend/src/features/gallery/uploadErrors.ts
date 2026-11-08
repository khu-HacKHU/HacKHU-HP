import { ApiError } from './api'
import { StorageUploadError } from './upload'

// 올리기 실패를 화면 문구로 바꾼다.
// 서버 message를 그대로 보여 주지 않고 상태와 code로 분기한다 (api.md 3절, CWE-209)
export function uploadErrorMessage(error: unknown): string {
  if (error instanceof StorageUploadError) {
    return '사진을 올리지 못했어요. 잠시 후 다시 시도해 주세요.'
  }
  if (!(error instanceof ApiError)) {
    return '앨범을 올리지 못했어요. 네트워크를 확인해 주세요.'
  }
  if (error.status === 401) return '로그인이 필요해요.'
  if (error.status === 403) return '동아리원만 앨범을 올릴 수 있어요.'
  switch (error.code) {
    case 'GALLERY_UNSUPPORTED_FORMAT':
      return 'jpg, png, webp 형식만 올릴 수 있어요.'
    case 'GALLERY_FILE_TOO_LARGE':
      return '사진 한 장은 10MB를 넘을 수 없어요.'
    case 'GALLERY_FUTURE_EVENT_DATE':
      return '활동 날짜는 오늘까지만 고를 수 있어요.'
    case 'GALLERY_INVALID_UPLOAD':
      return '올린 사진을 확인할 수 없어요. 사진을 다시 골라 올려 주세요.'
    case 'GALLERY_TOO_MANY_REQUESTS':
      return '올리기 요청이 너무 많아요. 잠시 후 다시 시도해 주세요.'
  }
  if (error.status === 400) return '입력한 내용을 다시 확인해 주세요.'
  return '앨범을 올리지 못했어요. 잠시 후 다시 시도해 주세요.'
}

// 서버 오류(5xx)면 이미 올린 사진으로 앨범 만들기만 다시 시도할 수 있다 (4-2 4절 4번)
export function canRetryCreate(error: unknown): boolean {
  return error instanceof ApiError && error.status >= 500
}
