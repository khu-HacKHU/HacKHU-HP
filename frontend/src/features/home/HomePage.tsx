import { useEffect, useState } from 'react'
import { AlbumCard } from '../gallery/AlbumCard'
import { fetchAlbums } from '../gallery/api'
import type { AlbumSummary } from '../gallery/types'
import { CLUB_NAME, ONE_LINE_INTRO } from './clubIntro'
import { fetchRecentNotices, toKoreanDate } from './notices'
import type { NoticeSummary } from './notices'

export const RECENT_ALBUM_COUNT = 4

// 로그인한 사람의 역할 (auth backend 4-2). null = 로그인하지 않음
export type Role = 'PENDING' | 'MEMBER' | 'STAFF' | 'ADMIN'

// 메인 화면에서 갈 수 있는 곳.
// 라우터가 정해지지 않아서 이동은 부모에게 맡긴다. 라우터가 정해지면 링크로 바꾼다
export type HomeDestination =
  | { to: 'intro' }
  | { to: 'notices' }
  | { to: 'notice'; id: number }
  | { to: 'gallery' }
  | { to: 'album'; id: number }
  | { to: 'signup' }
  | { to: 'login' }

type HomePageProps = {
  // 로그인 상태(#26)가 생기면 연결한다. 그 전까지는 로그인하지 않은 방문자로 본다
  role?: Role | null
  onNavigate: (destination: HomeDestination) => void
}

// 영역 하나의 불러오기 상태. 영역마다 따로 두어 하나가 실패해도 나머지는 보인다
type Section<T> =
  { status: 'loading' } | { status: 'error' } | { status: 'done'; items: T[] }

function useSection<T>(load: () => Promise<T[]>): Section<T> {
  const [section, setSection] = useState<Section<T>>({ status: 'loading' })
  useEffect(() => {
    let active = true // 화면을 떠난 뒤 온 응답은 무시한다
    load()
      .then((items) => active && setSection({ status: 'done', items }))
      .catch(() => active && setSection({ status: 'error' }))
    return () => {
      active = false
    }
  }, [load])
  return section
}

// useSection에 넘기는 함수는 컴포넌트 밖에 두어 렌더링마다 다시 불러오지 않게 한다

const loadAlbums = () =>
  fetchAlbums(0, RECENT_ALBUM_COUNT).then((page) => page.content)

// 메인 화면: 동아리 소개, 최근 공지, 최근 앨범, 가입·로그인 안내 (홈 USER-STORIES 1절)
export function HomePage({ role = null, onNavigate }: HomePageProps) {
  const notices = useSection<NoticeSummary>(fetchRecentNotices)
  const albums = useSection<AlbumSummary>(loadAlbums)

  return (
    <div>
      <section aria-labelledby="home-intro">
        <h2 id="home-intro">{CLUB_NAME}</h2>
        <p>{ONE_LINE_INTRO}</p>
        <button type="button" onClick={() => onNavigate({ to: 'intro' })}>
          동아리 소개 보기
        </button>
      </section>

      <AccountNotice role={role} onNavigate={onNavigate} />

      <section aria-labelledby="home-notices">
        <h2 id="home-notices">최근 공지</h2>
        {notices.status === 'loading' && <p>불러오는 중...</p>}
        {notices.status === 'error' && (
          <p role="alert">공지를 불러오지 못했어요.</p>
        )}
        {notices.status === 'done' &&
          (notices.items.length === 0 ? (
            <p>아직 올라온 공지가 없어요.</p>
          ) : (
            <ul>
              {notices.items.map((notice) => (
                <li key={notice.id}>
                  <button
                    type="button"
                    onClick={() => onNavigate({ to: 'notice', id: notice.id })}
                  >
                    <strong>{notice.title}</strong>
                    <time dateTime={notice.createdAt}>
                      {toKoreanDate(notice.createdAt)}
                    </time>
                  </button>
                </li>
              ))}
            </ul>
          ))}
        <button type="button" onClick={() => onNavigate({ to: 'notices' })}>
          공지 전체 보기
        </button>
      </section>

      <section aria-labelledby="home-albums">
        <h2 id="home-albums">최근 앨범</h2>
        {albums.status === 'loading' && <p>불러오는 중...</p>}
        {albums.status === 'error' && (
          <p role="alert">앨범을 불러오지 못했어요.</p>
        )}
        {albums.status === 'done' &&
          (albums.items.length === 0 ? (
            <p>아직 올라온 앨범이 없어요.</p>
          ) : (
            <ul>
              {albums.items.map((album) => (
                <AlbumCard
                  key={album.id}
                  album={album}
                  onSelect={(id) => onNavigate({ to: 'album', id })}
                />
              ))}
            </ul>
          ))}
        <button type="button" onClick={() => onNavigate({ to: 'gallery' })}>
          갤러리 가기
        </button>
      </section>
    </div>
  )
}

// 가입·로그인 안내. 방문자에게는 링크, 승인 대기 회원에게는 안내, 동아리원에게는 아무것도 없다
function AccountNotice({
  role,
  onNavigate,
}: {
  role: Role | null
  onNavigate: (destination: HomeDestination) => void
}) {
  if (role === null) {
    return (
      <section aria-label="가입과 로그인">
        <button type="button" onClick={() => onNavigate({ to: 'signup' })}>
          가입하기
        </button>
        <button type="button" onClick={() => onNavigate({ to: 'login' })}>
          로그인
        </button>
      </section>
    )
  }
  if (role === 'PENDING') {
    return (
      <p role="status">
        가입 승인을 기다리고 있어요. 운영진이 승인하면 모든 기능을 쓸 수 있어요.
      </p>
    )
  }
  return null
}
