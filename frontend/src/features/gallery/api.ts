import type { AlbumDetail, AlbumSummary, Page } from './types'

//API 호출 실패 시
export class ApiError extends Error {
  status: number
  code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

//API 호출 실패 시, 응답을 ApiError로 변환
async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = await res.json()
    return new ApiError(res.status, body.code ?? 'UNKNOWN', body.message ?? '')
  } catch {
    return new ApiError(res.status, 'UNKNOWN', '')
  }
}

//앨범 목록 받기: GET /api/v1/albums?page=0&size=20
export async function fetchAlbums(
  page = 0,
  size = 20,
): Promise<Page<AlbumSummary>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  const res = await fetch(`/api/v1/albums?${params}`)
  if (!res.ok) throw await toApiError(res)
  return res.json()
}

//앨범 상세 받기: GET /api/v1/albums/{id}
export async function fetchAlbum(id: number): Promise<AlbumDetail> {
  const res = await fetch(`/api/v1/albums/${id}`)
  if (!res.ok) throw await toApiError(res)
  return res.json()
}
