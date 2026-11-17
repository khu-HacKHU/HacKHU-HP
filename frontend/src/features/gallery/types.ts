//목록의 앨범 하나, (backend 4-2)
export type AlbumSummary = {
  id: number
  title: string
  eventDate: string
  coverPhotoUrl: string
  //앨범의 사진 수 (backend 4-2 1절). 카드에 보여 준다 (UI-DESIGN-SYSTEM.md 6-6)
  photoCount: number
  createdAt: string
}

//페이지 응답, (backend 4-2)
export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

//앨범 안의 사진 하나
export type Photo = {
  id: number
  url: string
  order: number
}

//앨범 상세
export type AlbumDetail = {
  id: number
  title: string
  eventDate: string
  //삭제 버튼을 보여줄지 서버가 판단한 결과 (backend 4-2 2절). 비로그인이면 false다
  canDelete: boolean
  photos: Photo[]
  createdAt: string
}
