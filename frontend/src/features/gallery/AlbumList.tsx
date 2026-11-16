import { AlbumCard } from './AlbumCard'
import type { AlbumSummary } from './types'

// 이 컴포넌트가 받는 props의 모양
type AlbumListProps = {
  albums: AlbumSummary[]
  onSelect: (id: number) => void
  disabled?: boolean
}

// 앨범 목록. 앨범이 없으면 빈 목록 안내를 보여 준다 (#37 완료 조건)
export function AlbumList({
  albums,
  onSelect,
  disabled = false,
}: AlbumListProps) {
  if (albums.length === 0) {
    return <p>아직 올라온 앨범이 없어요.</p>
  }
  return (
    <ul>
      {albums.map((album) => (
        <AlbumCard
          key={album.id}
          album={album}
          onSelect={onSelect}
          disabled={disabled}
        />
      ))}
    </ul>
  )
}
