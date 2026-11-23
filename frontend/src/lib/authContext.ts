/*
 * 로그인 상태 Context (docs/spec/auth/frontend/4-2-QUALITY.md).
 *
 * 컴포넌트(AuthProvider)와 훅(useAuth)을 같은 파일에서 내보내면 Fast refresh가 꺼진다.
 * 그래서 Context만 여기 둔다.
 */
import { createContext } from 'react'
import type { Session } from './session'

export type AuthState = {
  session: Session | null
  /** 새로고침 뒤 로그인 상태를 되살리는 중인지. 끝나기 전에는 로그인 여부를 단정하지 않는다. */
  restoring: boolean
}

export const AuthContext = createContext<AuthState | null>(null)
