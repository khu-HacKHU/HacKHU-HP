/*
 * 메인 화면이 쓰는 타입.
 *
 * 갤러리와 모양이 겹치지만 features/gallery/ 안쪽을 가져오지 않는다 (frontend/AGENTS.md
 * "폴더 구조"). 두 기능이 같은 타입을 쓸 일이 더 늘면 그때 lib/로 올린다.
 */

/** 메인 화면의 최근 앨범 하나 (gallery backend 4-2). */
export type RecentAlbum = {
  id: number
  title: string
  eventDate: string
  coverPhotoUrl: string | null
  photoCount: number
}

/** 페이지 응답 (docs/conventions/api.md). */
export type AlbumPage = {
  content: RecentAlbum[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** 메인 화면의 최근 공지 하나. 게시판 API가 생기면 그 응답 모양으로 맞춘다. */
export type RecentNotice = {
  id: number
  title: string
  createdAt: string
}
