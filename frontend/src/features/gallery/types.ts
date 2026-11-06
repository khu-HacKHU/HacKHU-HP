//목록의 앨범 하나, (backend 4-2)
export type AlbumSummary = {
  id: number
  title: string
  eventDate: string
  coverPhotoUrl: string
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
  photos: Photo[]
  createdAt: string
}
