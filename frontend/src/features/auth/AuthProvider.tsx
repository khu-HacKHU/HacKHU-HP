/*
 * 로그인 상태를 화면에 연결한다 (docs/spec/auth/frontend/4-2-QUALITY.md).
 *
 * 상태 자체는 session.ts의 모듈 변수에 있다. 여기서는 그것을 React가 다시 그릴 수 있게
 * 이어 주기만 한다 (frontend/AGENTS.md "상태 관리").
 */
import {
  useEffect,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { refreshSession } from '../../lib/api'
import { AuthContext } from './authContext'
import { getSession, replaceSession, subscribe } from './session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useSyncExternalStore(subscribe, getSession, getSession)
  const [restoring, setRestoring] = useState(true)

  useEffect(() => {
    let cancelled = false
    // access 토큰은 메모리에만 있어 새로고침하면 사라진다. refresh 쿠키로 다시 받는다.
    // 쿠키가 없거나 세션이 끊겼으면 null이 오고 로그인하지 않은 상태로 둔다.
    refreshSession().then((token) => {
      if (cancelled) return
      if (token !== null) {
        replaceSession(token.user, token.accessToken)
      }
      setRestoring(false)
    })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <AuthContext.Provider value={{ session, restoring }}>
      {children}
    </AuthContext.Provider>
  )
}
