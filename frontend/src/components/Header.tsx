/*
 * 공통 헤더 (#26, #57).
 *
 * 구성은 디자인 시스템 7절을 따른다. 왼쪽 로고, 가운데 메뉴 다섯 개, 오른쪽 로그인 상태다.
 * 760px보다 좁으면 가운데 메뉴와 관리 메뉴를 접고 메뉴 버튼으로 연다.
 *
 * 권한에 따라 메뉴를 감추는 것은 화면 편의일 뿐이다. 권한 검사는 서버가 한다
 * (frontend/AGENTS.md "코드 규칙").
 *
 * 두 기능 이상이 쓰므로 src/components/에 둔다 (frontend/AGENTS.md "폴더 구조").
 */
import { useEffect, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router'
import { logout } from '../features/auth/api'
import type { AuthUser } from '../lib/session'
import { useAuth } from '../lib/useAuth'
import s from './Header.module.css'

const ROLE_LABEL: Record<AuthUser['role'], string> = {
  PENDING: '승인 대기',
  MEMBER: '동아리원',
  STAFF: '운영진',
  ADMIN: '관리자',
}

const ROLE_CLASS: Record<AuthUser['role'], string> = {
  PENDING: s.pending,
  MEMBER: s.member,
  STAFF: s.staff,
  // 관리자 배지는 운영진과 다르게 채운다. 역할 변경을 부탁할 사람을 알아볼 수 있게 한다 (6-4).
  ADMIN: s.admin,
}

/** 가운데 메뉴 (7절). 자유게시판과 자료게시판은 방문자에게도 보인다. */
const MENU = [
  { to: '/notice', label: '공지' },
  { to: '/free', label: '자유게시판' },
  { to: '/archive', label: '자료게시판' },
  { to: '/gallery', label: '갤러리' },
  { to: '/about', label: '동아리 소개' },
]

export function Header() {
  const { session, restoring } = useAuth()
  const navigate = useNavigate()
  const [leaving, setLeaving] = useState(false)
  const [logoutFailed, setLogoutFailed] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  // Esc로 닫는다. 키보드만 쓰는 사람이 메뉴에 갇히지 않게 한다 (8절).
  useEffect(() => {
    if (!menuOpen) return
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setMenuOpen(false)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [menuOpen])

  async function onLogout() {
    setLeaving(true)
    setLogoutFailed(false)
    try {
      await logout()
      navigate('/')
    } catch {
      // 서버가 로그인 세션을 폐기하지 못했다. 화면만 비우고 넘어가면 refresh 쿠키가 남아
      // 공용 PC에서 다음 사람이 새로고침만으로 앞사람으로 로그인된다 (A07, CWE-613).
      // 조용히 넘기지 않고 다시 시도하게 한다. 로그아웃은 쿠키로만 하므로 재시도에 토큰이 없어도 된다.
      setLogoutFailed(true)
    } finally {
      setLeaving(false)
    }
  }

  const role = session?.user.role
  const isStaff = role === 'STAFF' || role === 'ADMIN'

  return (
    <header className={s.header}>
      {/* 키보드로 첫 탭을 누르면 보인다. 메뉴를 다 지나지 않고 본문으로 간다 (8절). */}
      <a className={s.skip} href="#main">
        본문으로 건너뛰기
      </a>

      <div className={s.inner}>
        <Link className={s.logo} to="/">
          {/* 이름이 Hack과 KHU(경희대)를 합친 말이라 KHU를 다른 색으로 둔다. */}
          Hac<span className={s.logoMark}>KHU</span>
        </Link>

        <button
          className={s.burger}
          type="button"
          aria-expanded={menuOpen}
          aria-controls="header-menu"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span className={s.burgerIcon} aria-hidden="true">
            {menuOpen ? '✕' : '☰'}
          </span>
          {menuOpen ? '메뉴 닫기' : '메뉴 열기'}
        </button>

        <nav
          className={s.menu}
          id="header-menu"
          data-open={menuOpen}
          aria-label="주요 메뉴"
        >
          {MENU.map((item) => (
            <NavLink
              className={s.menuLink}
              key={item.to}
              to={item.to}
              // 좁은 화면에서 메뉴를 누르면 접는다. 펼친 메뉴가 새 화면 위에 남지 않게 한다.
              onClick={() => setMenuOpen(false)}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* 운영진 이상만 본다 (7절). 관리자의 역할 변경은 회원 관리 화면 안에 있다. */}
        {isStaff && (
          <div className={s.manage} data-open={menuOpen}>
            <span className={s.manageLabel}>관리</span>
            <NavLink
              className={s.menuLink}
              to="/admin/approvals"
              onClick={() => setMenuOpen(false)}
            >
              가입 승인
            </NavLink>
            <NavLink
              className={s.menuLink}
              to="/admin/members"
              onClick={() => setMenuOpen(false)}
            >
              회원 관리
            </NavLink>
          </div>
        )}

        {/* 복구가 끝나기 전에는 로그인 여부를 단정하지 않는다. 버튼이 깜빡이는 것을 막는다. */}
        {restoring ? null : session === null ? (
          <div className={s.auth}>
            <Link className={s.button} to="/login">
              로그인
            </Link>
            <Link className={s.primaryButton} to="/signup">
              가입하기
            </Link>
          </div>
        ) : (
          <div className={s.auth}>
            <span className={s.name}>{session.user.name}</span>
            <span className={`${s.badge} ${ROLE_CLASS[session.user.role]}`}>
              {ROLE_LABEL[session.user.role]}
            </span>
            <Link className={s.button} to="/me">
              내 정보
            </Link>
            <button
              className={s.button}
              type="button"
              onClick={onLogout}
              disabled={leaving}
            >
              로그아웃
            </button>
          </div>
        )}
      </div>

      {/* 세션이 비워져 로그아웃 버튼이 사라진 뒤에도 보여야 해서 세션 조건 밖에 둔다. */}
      {logoutFailed && (
        <p className={s.alert} role="alert">
          로그아웃하지 못했습니다. 공용 PC라면 꼭 다시 시도해 주세요.{' '}
          <button
            className={s.button}
            type="button"
            onClick={onLogout}
            disabled={leaving}
          >
            다시 시도
          </button>
        </p>
      )}
    </header>
  )
}
