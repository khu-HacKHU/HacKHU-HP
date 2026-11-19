/*
 * 공통 헤더 (#26).
 *
 * 로그인 전에는 로그인, 가입 버튼. 로그인 뒤에는 이름과 로그아웃 버튼.
 * 메뉴는 홈 화면(#57)에서 채운다.
 *
 * 두 기능 이상이 쓰므로 src/components/에 둔다 (frontend/AGENTS.md "폴더 구조").
 */
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { logout } from '../features/auth/api'
import { useAuth } from '../features/auth/useAuth'
import type { AuthUser } from '../features/auth/session'
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
  ADMIN: s.staff,
}

export function Header() {
  const { session, restoring } = useAuth()
  const navigate = useNavigate()
  const [leaving, setLeaving] = useState(false)

  async function onLogout() {
    setLeaving(true)
    try {
      await logout()
      navigate('/')
    } finally {
      setLeaving(false)
    }
  }

  return (
    <header className={s.header}>
      <div className={s.inner}>
        <Link className={s.logo} to="/">
          HacKHU
        </Link>

        {/* 복구가 끝나기 전에는 로그인 여부를 단정하지 않는다. 버튼이 깜빡이는 것을 막는다. */}
        {restoring ? null : session === null ? (
          <nav className={s.right}>
            <Link className={s.button} to="/login">
              로그인
            </Link>
            <Link className={s.button} to="/signup">
              가입
            </Link>
          </nav>
        ) : (
          <div className={s.right}>
            <span className={s.name}>{session.user.name}</span>
            <span className={`${s.badge} ${ROLE_CLASS[session.user.role]}`}>
              {ROLE_LABEL[session.user.role]}
            </span>
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
    </header>
  )
}
