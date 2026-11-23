import { useContext } from 'react'
import { AuthContext, type AuthState } from './authContext'

/** 지금 로그인 상태. AuthProvider 안에서만 쓴다. */
export function useAuth(): AuthState {
  const state = useContext(AuthContext)
  if (state === null) {
    throw new Error('useAuth는 AuthProvider 안에서만 쓸 수 있습니다.')
  }
  return state
}
