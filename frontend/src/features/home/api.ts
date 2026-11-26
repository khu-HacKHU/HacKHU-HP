/*
 * 메인 화면의 API 호출 (frontend/AGENTS.md "API 호출").
 *
 * 공통 처리는 lib/api.ts의 request가 한다. 여기서 fetch를 직접 부르지 않는다.
 */
import { request } from '../../lib/api'
import type { AlbumPage } from './types'

/** 활동 날짜가 최근인 앨범 몇 개. 메인 화면은 4개를 쓴다 (home/USER-STORIES 1절). */
export function fetchRecentAlbums(size = 4): Promise<AlbumPage> {
  const params = new URLSearchParams({ page: '0', size: String(size) })
  return request<AlbumPage>(`/api/v1/albums?${params}`)
}
