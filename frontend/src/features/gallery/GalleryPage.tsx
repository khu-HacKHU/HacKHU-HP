import { useEffect, useState } from 'react'
import { AlbumDetailView } from './AlbumDetailView'
import { AlbumList } from './AlbumList'
import { ApiError, deleteAlbum, fetchAlbum, fetchAlbums } from './api'
import type { CSSProperties } from 'react'
import type { AlbumDetail, AlbumSummary } from './types'

// 경고 안내 상자: panel 배경, 왼쪽 3px coral 선 (UI-DESIGN-SYSTEM.md 6-3)
// 값만 문서에서 가져온 임시 인라인 스타일이다
const warningBoxStyle: CSSProperties = {
  background: '#181a33',
  borderLeft: '3px solid #f0727a',
  color: '#ece8f4',
  borderRadius: 6,
  padding: '0.75rem 1rem',
}

// 갤러리 화면: 목록을 보여 주고, 앨범을 고르면 상세로 바꾼다
export function GalleryPage() {
  const [albums, setAlbums] = useState<AlbumSummary[] | null>(null) // null = 아직 불러오는 중
  const [listError, setListError] = useState<string | null>(null) // 목록을 못 불러왔을 때
  const [selected, setSelected] = useState<AlbumDetail | null>(null) // null = 목록 보는 중
  const [albumError, setAlbumError] = useState<string | null>(null) // 고른 앨범을 못 불러오거나 못 지웠을 때
  const [loadingId, setLoadingId] = useState<number | null>(null) // 불러오는 중인 앨범 id
  const [deleting, setDeleting] = useState(false) // 삭제 요청을 보낸 뒤 응답을 기다리는 중

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
    setAlbumError(null)
    try {
      setSelected(await fetchAlbum(id))
    } catch (e) {
      // 화면은 message가 아니라 code로 분기한다 (api.md 3절)
      if (e instanceof ApiError && e.code === 'GALLERY_ALBUM_NOT_FOUND') {
        setAlbumError('앨범을 찾을 수 없어요.')
      } else {
        setAlbumError('앨범을 불러오지 못했어요.')
      }
    } finally {
      setLoadingId(null)
    }
  }

  // 지워진 앨범은 목록에서 바로 빼고 목록 화면으로 돌아간다 (USER-STORIES 3절)
  function dropAlbum(id: number) {
    setAlbums((prev) => prev && prev.filter((album) => album.id !== id))
    setSelected(null)
  }

  async function handleDelete() {
    if (!selected || deleting) return
    const id = selected.id
    setDeleting(true)
    setAlbumError(null)
    try {
      await deleteAlbum(id)
      dropAlbum(id)
    } catch (e) {
      const code = e instanceof ApiError ? e.code : ''
      if (code === 'GALLERY_ALBUM_NOT_FOUND') {
        // 다른 곳에서 이미 지워진 앨범이다. 목록에 남겨 두면 틀린 화면이 된다
        setAlbumError('이미 삭제된 앨범이에요.')
        dropAlbum(id)
      } else if (code === 'GALLERY_FORBIDDEN') {
        setAlbumError('이 앨범을 지울 권한이 없어요.')
      } else {
        setAlbumError('앨범을 삭제하지 못했어요.')
      }
    } finally {
      setDeleting(false)
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
    return (
      <div>
        {albumError && (
          <p role="alert" style={warningBoxStyle}>
            {albumError}
          </p>
        )}
        <AlbumDetailView
          album={selected}
          onBack={() => {
            setSelected(null)
            setAlbumError(null)
          }}
          onDelete={handleDelete}
          deleting={deleting}
        />
      </div>
    )
  }
  if (albums === null) return <p>불러오는 중...</p>

  // 앨범을 못 불러와도, 못 지워도 목록은 그대로 두고 그 위에 안내만 띄운다
  return (
    <div>
      {albumError && (
        <p role="alert" style={warningBoxStyle}>
          {albumError}
        </p>
      )}
      {loadingId !== null && <p>앨범을 불러오는 중...</p>}
      <AlbumList
        albums={albums}
        onSelect={handleSelect}
        disabled={loadingId !== null}
      />
    </div>
  )
}
