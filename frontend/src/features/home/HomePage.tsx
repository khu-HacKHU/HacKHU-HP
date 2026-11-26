/*
 * 메인 화면 (#57).
 *
 * 영역은 home/USER-STORIES.md 1절을 따른다. 소개, 최근 공지, 최근 앨범, 안내다.
 * 빛 번짐과 격자 배경은 이 화면에서만 쓴다 (UI 디자인 시스템 1절).
 *
 * 배치 규칙 (design-taste-frontend 스킬):
 *   - 섹션 하나가 정확히 한 화면이다. 히어로는 첫 화면을 혼자 쓰고 다음 섹션이 보이지 않는다.
 *   - 섹션 사이를 선으로 나누지 않는다. 높이와 여백으로만 나눈다.
 *   - 제목은 섹션 맨 위에 둔다. 내용을 화면 가운데에 세로로 띄우지 않는다.
 *   - 첫 화면에 숫자나 날짜 같은 사실 하나를 둔다.
 *   - 주요 동작은 헤더, 히어로, 닫는 섹션에서 같은 말로 쓴다.
 *   - 버튼은 동작과 대상을 함께 쓴다. "자세히 보기" 같은 말은 쓰지 않는다.
 */
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../lib/useAuth'
import { fetchRecentAlbums } from './api'
import type { RecentAlbum, RecentNotice } from './types'
import s from './HomePage.module.css'

/*
 * 임시 표본이다. 게시판 API가 아직 없어서 목록 배치를 보기 위해 둔다.
 * 게시판 목록 조회 API가 생기면 이 상수를 지우고 api.ts에서 받아 온다.
 */
const SAMPLE_NOTICES: RecentNotice[] = [
  {
    id: 5,
    title: '2026학년도 2학기 정기 세미나 일정',
    createdAt: '2026-11-18',
  },
  { id: 4, title: '교내 CTF 예선 결과와 본선 안내', createdAt: '2026-11-04' },
  { id: 3, title: '연합 CTF 참가 신청을 받습니다', createdAt: '2026-10-21' },
  { id: 2, title: '동아리방 출입 수칙 변경 안내', createdAt: '2026-10-07' },
  { id: 1, title: '신입부원 모집 결과 발표', createdAt: '2026-09-23' },
]

export function HomePage() {
  const { session, restoring } = useAuth()
  const visitor = !restoring && session === null

  const [albums, setAlbums] = useState<RecentAlbum[] | null>(null)
  const [albumsFailed, setAlbumsFailed] = useState(false)

  useEffect(() => {
    let alive = true
    fetchRecentAlbums(4)
      .then((page) => {
        if (alive) setAlbums(page.content)
      })
      .catch(() => {
        if (alive) setAlbumsFailed(true)
      })
    return () => {
      alive = false
    }
  }, [])

  return (
    <>
      <Hero visitor={visitor} />

      <section className={s.section} id="notice" aria-labelledby="notice-title">
        <div className={s.wrap}>
          <SectionHead
            id="notice-title"
            label="NOTICE"
            title="최근 공지"
            note="공지 목록은 준비 중입니다"
          />

          {/* ls -l 형 목록 (5절). 날짜는 mono와 muted, 제목은 sans. */}
          <ul className={s.noticeList}>
            {SAMPLE_NOTICES.map((notice) => (
              <li className={s.noticeRow} key={notice.id}>
                <time className={s.noticeDate} dateTime={notice.createdAt}>
                  {notice.createdAt}
                </time>
                <span className={s.noticeTitle}>{notice.title}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section
        className={s.section}
        id="gallery"
        aria-labelledby="gallery-title"
      >
        <div className={s.wrap}>
          <SectionHead
            id="gallery-title"
            label="GALLERY"
            title="활동 사진"
            link={{ to: '/gallery', text: '갤러리 전체 보기' }}
          />
          <AlbumArea albums={albums} failed={albumsFailed} />
        </div>
      </section>

      {/* 닫는 글과 푸터는 한 섹션이다. wrap이 두 줄을 가진다. */}
      <footer className={s.closing}>
        <div className={s.closingWrap}>
          <div className={s.closingRow}>
            {/* 이 화면의 글자 한 번: 동아리 이름을 크게 둔다. */}
            {/* 히어로의 워드마크와 같은 두 가지 색으로 둔다. 같은 이름이 화면마다 다르게
                보이면 안 된다. */}
            <p className={s.moment} aria-hidden="true">
              Hac<span className={s.wordmarkMark}>KHU</span>
            </p>
            <h2 className={s.closingTitle}>동아리원 전용 게시판</h2>
            <p className={s.closingLead}>
              자유게시판과 자료게시판은 가입 후 운영진 승인 시 이용할 수
              있습니다.
              <br />
              홈페이지 회원가입은 동아리 지원과 별개입니다.
            </p>
            {visitor && (
              <div className={s.actions}>
                <Link className={s.primaryButton} to="/signup">
                  가입하기
                </Link>
                <Link className={s.button} to="/login">
                  로그인
                </Link>
              </div>
            )}
          </div>

          <div className={s.legalRow}>
            <p className={s.footerName}>경희대학교 정보보안 동아리 HacKHU</p>
            <Link className={s.footerLink} to="/about">
              소개 보기
            </Link>
          </div>
        </div>
      </footer>
    </>
  )
}

type HeroProps = {
  visitor: boolean
}

/*
 * 첫 화면은 읽는 화면이 아니라 보는 화면이다. 소개 글은 소개 화면에 있으므로 여기서는
 * 영문 한 줄, 큰 워드마크, 동작 하나만 둔다. 설명을 두 화면에 똑같이 적지 않는다.
 */
function Hero({ visitor }: HeroProps) {
  return (
    <section className={s.hero} aria-labelledby="hero-title">
      {/* 빛 번짐, 격자, 입자. 메인 화면에만 쓴다 (1절). 읽을 내용이 없는 장식이다. */}
      <div className={s.glow} aria-hidden="true" />
      <div className={s.grid} aria-hidden="true" />
      <div className={s.grain} aria-hidden="true" />

      <div className={s.heroWrap}>
        <div className={s.heroText} data-enter="">
          <p className={s.eyebrow}>
            KYUNGHEE&apos;S FIRST INFORMATION SECURITY SOCIETY
          </p>

          <h1 className={s.wordmark} id="hero-title">
            {/* 그림처럼 큰 글자라, 화면 읽기 프로그램에는 동아리 이름을 한국어로 들려준다. */}
            <span className={s.srOnly}>
              경희대학교 최초의 정보보안 동아리 HacKHU
            </span>
            <span aria-hidden="true">
              Hac<span className={s.wordmarkMark}>KHU</span>
            </span>
          </h1>

          <div className={s.actions}>
            {visitor ? (
              <Link className={s.primaryButton} to="/signup">
                가입하기
                <span className={s.arrow} aria-hidden="true">
                  →
                </span>
              </Link>
            ) : (
              <Link className={s.primaryButton} to="/gallery">
                활동 사진 보기
                <span className={s.arrow} aria-hidden="true">
                  →
                </span>
              </Link>
            )}
          </div>
        </div>
      </div>

      <p className={s.scrollHint} aria-hidden="true">
        SCROLL
      </p>
    </section>
  )
}

function AlbumArea({
  albums,
  failed,
}: {
  albums: RecentAlbum[] | null
  failed: boolean
}) {
  if (failed) {
    return (
      <p className={s.warning} role="alert">
        앨범을 불러오지 못했습니다. 잠시 뒤 다시 열어 주세요.
      </p>
    )
  }
  if (albums === null) {
    return <p className={s.empty}>앨범을 불러오는 중입니다.</p>
  }
  if (albums.length === 0) {
    return <p className={s.empty}>아직 앨범이 없습니다.</p>
  }
  return (
    <ul className={s.albumGrid}>
      {albums.map((album) => (
        <li key={album.id}>
          <Link className={s.albumCard} to="/gallery">
            <span className={s.albumFrame}>
              {album.coverPhotoUrl === null ? (
                <span className={s.albumCoverEmpty} aria-hidden="true" />
              ) : (
                <img
                  className={s.albumCover}
                  src={album.coverPhotoUrl}
                  alt={`${album.title} 대표 사진`}
                  loading="lazy"
                />
              )}
            </span>
            <strong className={s.albumTitle}>{album.title}</strong>
            <span className={s.albumMeta}>
              <time dateTime={album.eventDate}>{album.eventDate}</time>
              <span>사진 {album.photoCount}장</span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

type SectionHeadProps = {
  id: string
  label: string
  title: string
  link?: { to: string; text: string }
  note?: string
}

/* 섹션 이름표와 한국어 제목 (6-7절). 이름표만으로 화면을 설명하지 않는다. */
function SectionHead({ id, label, title, link, note }: SectionHeadProps) {
  return (
    <div className={s.sectionHead}>
      <div>
        <p className={s.sectionLabel}>
          <span className={s.dot} aria-hidden="true" />
          {label}
        </p>
        <h2 className={s.sectionTitle} id={id}>
          {title}
        </h2>
      </div>
      {link !== undefined && (
        <Link className={s.moreLink} to={link.to}>
          {link.text}
        </Link>
      )}
      {note !== undefined && <span className={s.note}>{note}</span>}
    </div>
  )
}
