import { useEffect, useState } from 'react'
import { AlbumDetailView } from './AlbumDetailView'
import { AlbumList } from './AlbumList'
import { ApiError, fetchAlbum, fetchAlbums } from './api'
import type { AlbumDetail, AlbumSummary } from './types'

// 갤러리 화면: 목록을 보여 주고, 앨범을 고르면 상세로 바꾼다
export function GalleryPage() {
  const [albums, setAlbums] = useState<AlbumSummary[] | null>(null) // null = 아직 불러오는 중
  const [listError, setListError] = useState<string | null>(null) // 목록을 못 불러왔을 때
  const [selected, setSelected] = useState<AlbumDetail | null>(null) // null = 목록 보는 중
  const [detailError, setDetailError] = useState<string | null>(null) // 상세를 못 불러왔을 때
  const [loadingId, setLoadingId] = useState<number | null>(null) // 불러오는 중인 앨범 id

  // 서버에 목록을 요청한다. 결과는 응답이 왔을 때 저장한다
  function requestAlbums() {
    fetchAlbums()
      .then((page) => setAlbums(page.content))
      .catch(() => setListError('앨범 목록을 불러오지 못했어요.'))
  }

  // 화면이 처음 나타날 때 한 번 목록을 불러온다
  useEffect(() => {
    requestAlbums()
  }, [])

  // "다시 시도": 오류를 지우고 "불러오는 중"으로 되돌린 뒤 다시 요청한다
  function retryAlbums() {
    setListError(null)
    setAlbums(null)
    requestAlbums()
  }

  async function handleSelect(id: number) {
    if (loadingId !== null) return // 이미 다른 앨범을 불러오는 중이면 무시한다
    setLoadingId(id)
    setDetailError(null)
    try {
      setSelected(await fetchAlbum(id))
    } catch (e) {
      // 화면은 message가 아니라 code로 분기한다 (api.md 3절)
      if (e instanceof ApiError && e.code === 'GALLERY_ALBUM_NOT_FOUND') {
        setDetailError('앨범을 찾을 수 없어요.')
      } else {
        setDetailError('앨범을 불러오지 못했어요.')
      }
    } finally {
      setLoadingId(null)
    }
  }

  // 목록을 못 불러왔으면 보여 줄 것이 없으니 화면 전체에 안내와 다시 시도 버튼을 둔다
  if (listError) {
    return (
      <div>
        <p role="alert">{listError}</p>
        <button type="button" onClick={retryAlbums}>
          다시 시도
        </button>
      </div>
    )
  }
  if (selected) {
    return <AlbumDetailView album={selected} onBack={() => setSelected(null)} />
  }
  if (albums === null) return <p>불러오는 중...</p>

  // 상세를 못 불러와도 목록은 그대로 두고 그 위에 안내만 띄운다
  return (
    <div>
      {detailError && <p role="alert">{detailError}</p>}
      {loadingId !== null && <p>앨범을 불러오는 중...</p>}
      <AlbumList
        albums={albums}
        onSelect={handleSelect}
        disabled={loadingId !== null}
      />
    </div>
  )
}
