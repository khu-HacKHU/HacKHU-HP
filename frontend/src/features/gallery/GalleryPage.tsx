import { useEffect, useState } from 'react'
import { AlbumDetailView } from './AlbumDetailView'
import { AlbumList } from './AlbumList'
import { ApiError, fetchAlbum, fetchAlbums } from './api'
import type { AlbumDetail, AlbumSummary } from './types'

// 갤러리 화면: 목록을 보여 주고, 앨범을 고르면 상세로 바꾼다
export function GalleryPage() {
  const [albums, setAlbums] = useState<AlbumSummary[] | null>(null)
  const [selected, setSelected] = useState<AlbumDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  // 화면이 처음 나타날 때 한 번 목록을 불러온다
  useEffect(() => {
    fetchAlbums()
      .then((page) => setAlbums(page.content))
      .catch(() => setError('앨범 목록을 불러오지 못했어요.'))
  }, [])

  async function handleSelect(id: number) {
    try {
      setSelected(await fetchAlbum(id))
    } catch (e) {
      // 화면은 message가 아니라 code로 분기한다 (api.md 3절)
      if (e instanceof ApiError && e.code === 'GALLERY_ALBUM_NOT_FOUND') {
        setError('앨범을 찾을 수 없어요.')
      } else {
        setError('앨범을 불러오지 못했어요.')
      }
    }
  }

  if (error) return <p role="alert">{error}</p>
  if (selected) {
    return <AlbumDetailView album={selected} onBack={() => setSelected(null)} />
  }
  if (albums === null) return <p>불러오는 중...</p>
  return <AlbumList albums={albums} onSelect={handleSelect} />
}
