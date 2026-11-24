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
  ADMIN: s.staff,
}

export function Header() {
  const { session, restoring } = useAuth()
  const navigate = useNavigate()
  const [leaving, setLeaving] = useState(false)
  const [logoutFailed, setLogoutFailed] = useState(false)

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
