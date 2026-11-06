import type { AlbumSummary } from './types'

// 이 컴포넌트가 받는 props의 모양
type AlbumCardProps = {
  album: AlbumSummary
  onSelect: (id: number) => void
}

// 목록의 앨범 하나: 대표 사진, 제목, 활동 날짜
export function AlbumCard({ album, onSelect }: AlbumCardProps) {
  return (
    <li>
      <button type="button" onClick={() => onSelect(album.id)}>
        <img src={album.coverPhotoUrl} alt={`${album.title} 대표 사진`} />
        <strong>{album.title}</strong>
        <time dateTime={album.eventDate}>{album.eventDate}</time>
      </button>
    </li>
  )
}
