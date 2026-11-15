import { useState } from 'react'
import type { CSSProperties } from 'react'
import type { AlbumDetail } from './types'

// 스타일링 방식은 아직 미정이라 값만 UI-DESIGN-SYSTEM.md에서 가져온 임시 인라인 스타일이다.
// 방식이 정해지면 옮긴다 (frontend/AGENTS.md "아직 정하지 않은 것")

// 위험 버튼: 투명 배경, coral 테두리와 글자 (6-1)
const dangerButtonStyle: CSSProperties = {
  background: 'transparent',
  border: '1px solid #f0727a',
  color: '#f0727a',
  borderRadius: 6,
  padding: '0.5rem 0.75rem',
  fontSize: '0.875rem',
  fontWeight: 500,
}

// 기본 버튼: 투명 배경, line-strong 테두리 (6-1)
const plainButtonStyle: CSSProperties = {
  ...dangerButtonStyle,
  border: '1px solid #676a96',
  color: 'inherit',
}

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
  const photos = [...album.photos].sort((a, b) => a.order - b.order)
  return (
    <section>
      <button type="button" onClick={onBack}>
        목록으로
      </button>
      <h2>{album.title}</h2>
      <time dateTime={album.eventDate}>{album.eventDate}</time>
      {/* canDelete는 화면 표시용이고 권한 검사는 서버가 한다 (backend 4-2 2절) */}
      {album.canDelete &&
        (confirming ? (
          <div>
            <p>앨범과 안의 사진이 모두 지워져요. 삭제할까요?</p>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button
                type="button"
                onClick={onDelete}
                disabled={deleting}
                style={dangerButtonStyle}
              >
                삭제
              </button>
              <button
                type="button"
                onClick={() => setConfirming(false)}
                disabled={deleting}
                style={plainButtonStyle}
              >
                취소
              </button>
            </div>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setConfirming(true)}
            style={dangerButtonStyle}
          >
            앨범 삭제
          </button>
        ))}
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
