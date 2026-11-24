import { useEffect, useRef, useState } from 'react'
import type { AlbumDetail } from './types'

// 이 컴포넌트가 받는 props의 모양
type AlbumDetailViewProps = {
  album: AlbumDetail
  onBack: () => void
  onDelete?: () => void
  deleting?: boolean // 삭제 요청이 끝날 때까지 버튼을 못 누르게 한다
}

// 앨범 상세: 제목, 활동 날짜, 사진 전체. 지울 수 있는 앨범이면 삭제 버튼도 둔다
export function AlbumDetailView({
  album,
  onBack,
  onDelete,
  deleting = false,
}: AlbumDetailViewProps) {
  const [confirming, setConfirming] = useState(false) // 삭제를 한 번 더 확인하는 중
  const confirmButtonRef = useRef<HTMLButtonElement>(null)

  // 확인 단계로 바뀌면 누르던 버튼이 사라져 포커스가 body로 떨어진다. 삭제 버튼으로 옮겨 준다
  useEffect(() => {
    if (confirming) confirmButtonRef.current?.focus()
  }, [confirming])

  const photos = [...album.photos].sort((a, b) => a.order - b.order)
  return (
    <section>
      <button type="button" className="button" onClick={onBack}>
        목록으로
      </button>
      <h2>{album.title}</h2>
      {/* 날짜는 왼쪽, 삭제는 오른쪽 끝. 좁은 화면에서는 줄이 나뉜다 */}
      <div className="album-meta">
        <time dateTime={album.eventDate}>{album.eventDate}</time>
        {/* canDelete는 화면 표시용이고 권한 검사는 서버가 한다 (backend 4-2 2절) */}
        {album.canDelete &&
          (confirming ? (
            <div className="album-confirm">
              <p>앨범과 안의 사진이 모두 지워져요. 삭제할까요?</p>
              <div className="confirm-actions">
                <button
                  type="button"
                  className="button button--danger"
                  onClick={onDelete}
                  disabled={deleting}
                  ref={confirmButtonRef}
                >
                  삭제
                </button>
                <button
                  type="button"
                  className="button"
                  onClick={() => setConfirming(false)}
                  disabled={deleting}
                >
                  취소
                </button>
              </div>
            </div>
          ) : (
            <button
              type="button"
              className="button button--danger"
              onClick={() => setConfirming(true)}
            >
              앨범 삭제
            </button>
          ))}
      </div>
      <ul className="album-photos">
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
