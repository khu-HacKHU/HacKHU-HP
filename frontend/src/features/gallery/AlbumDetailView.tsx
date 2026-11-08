import type { AlbumDetail } from './types'

// 이 컴포넌트가 받는 props의 모양
type AlbumDetailViewProps = {
  album: AlbumDetail
  onBack: () => void
}

// 앨범 상세: 제목, 활동 날짜, 사진 전체
export function AlbumDetailView({ album, onBack }: AlbumDetailViewProps) {
  const photos = [...album.photos].sort((a, b) => a.order - b.order)
  return (
    <section>
      <button type="button" onClick={onBack}>
        목록으로
      </button>
      <h2>{album.title}</h2>
      <time dateTime={album.eventDate}>{album.eventDate}</time>
      <ul>
        {photos.map((photo) => (
          <li key={photo.id}>
            <img
              src={photo.url}
              alt={`${album.title} 사진 ${photo.order + 1}`}
            />
          </li>
        ))}
      </ul>
    </section>
  )
}
