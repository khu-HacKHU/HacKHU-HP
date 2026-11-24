import { request } from '../../lib/api'
import type { AlbumDetail, AlbumSummary, Page } from './types'

// 공통 처리(credentials, access 토큰 헤더, 401 뒤 갱신, 오류 변환)는 lib/api.ts의 request가
// 한다 (frontend/AGENTS.md "API 호출"). 화면은 ApiError의 code로 분기한다
export { ApiError } from '../../lib/api'

//앨범 목록 받기: GET /api/v1/albums?page=0&size=20
export function fetchAlbums(page = 0, size = 20): Promise<Page<AlbumSummary>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  return request<Page<AlbumSummary>>(`/api/v1/albums?${params}`)
}

//앨범 상세 받기: GET /api/v1/albums/{id}
export function fetchAlbum(id: number): Promise<AlbumDetail> {
  return request<AlbumDetail>(`/api/v1/albums/${id}`)
}

//앨범 삭제: DELETE /api/v1/albums/{id}. 작성자 또는 관리자만 할 수 있어 토큰이 필요하다
export function deleteAlbum(id: number): Promise<void> {
  return request<void>(`/api/v1/albums/${id}`, { method: 'DELETE' })
}
