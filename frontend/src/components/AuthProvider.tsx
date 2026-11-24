/*
 * 로그인 상태를 화면에 연결한다 (docs/spec/auth/frontend/4-2-QUALITY.md).
 *
 * 상태 자체는 lib/session.ts의 모듈 변수에 있다. 여기서는 그것을 React가 다시 그릴 수 있게
 * 이어 주고, 요청 공통 처리가 알리는 인증 결과를 화면 이동으로 옮긴다
 * (frontend/AGENTS.md "상태 관리").
 */
import {
  useEffect,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { useNavigate } from 'react-router'
import { refreshSession } from '../lib/api'
import { AuthContext } from '../lib/authContext'
import {
  getSession,
  onSessionEvent,
  replaceSession,
  subscribe,
} from '../lib/session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useSyncExternalStore(subscribe, getSession, getSession)
  const [restoring, setRestoring] = useState(true)
  const navigate = useNavigate()

  // 요청 공통 처리(lib/api.ts)는 컴포넌트 밖이라 화면을 옮길 수 없다. 여기서 받아서 옮긴다.
  // 갱신까지 401이면 로그인 화면, 비밀번호 변경 필요 상태면 변경 화면이다 (auth 4-2-1).
  useEffect(
    () =>
      onSessionEvent((event) => {
        navigate(event === 'expired' ? '/login' : '/password', {
          replace: true,
        })
      }),
    [navigate],
  )

  useEffect(() => {
    let cancelled = false
    // access 토큰은 메모리에만 있어 새로고침하면 사라진다. refresh 쿠키로 다시 받는다.
    // 쿠키가 없거나 세션이 끊겼으면 로그인하지 않은 상태로 둔다.
    refreshSession().then((result) => {
      if (cancelled) return
      if (result.status === 'ok') {
        replaceSession(result.token.user, result.token.accessToken)
        // 복구할 때도 비밀번호 변경 화면으로 보낸다. 로그인할 때만 확인하면 새로고침한 사용자가
        // 원래 화면에 남아 모든 요청이 403으로 막힌다 (4-2-2).
        if (result.token.user.mustChangePassword) {
          navigate('/password', { replace: true })
        }
      }
      setRestoring(false)
    })
    return () => {
      cancelled = true
    }
  }, [navigate])

  return (
    <AuthContext.Provider value={{ session, restoring }}>
      {children}
    </AuthContext.Provider>
  )
}
