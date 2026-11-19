/*
 * 인증권한 API 호출 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-3~4-2-6).
 *
 * 공통 처리는 src/lib/api.ts의 request가 한다.
 */
import { request } from '../../lib/api'
import { clearSession, setSession, type AuthUser } from './session'

export type SignupInput = {
  email: string
  password: string
  name: string
}

/** 가입 응답 (4-2-3). 토큰을 주지 않는다. 가입만 하고 로그인시키지 않는다. */
export type SignupResult = {
  id: number
  email: string
  name: string
  role: AuthUser['role']
  createdAt: string
}

type TokenResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

/** 가입한다. 성공하면 승인 대기 회원이 된다. */
export function signup(input: SignupInput): Promise<SignupResult> {
  return request<SignupResult>('/api/v1/auth/signup', {
    method: 'POST',
    body: input,
  })
}

/** 로그인한다. 성공하면 세션을 세운다. 승인 대기 회원도 로그인된다 (4-2-4). */
export async function login(
  email: string,
  password: string,
): Promise<AuthUser> {
  const token = await request<TokenResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: { email, password },
  })
  setSession({ user: token.user, accessToken: token.accessToken })
  return token.user
}

/**
 * 로그아웃한다. 서버가 refresh 쿠키의 세션을 폐기하고 쿠키를 지운다.
 *
 * <p>요청이 실패해도 화면의 세션은 비운다. 쿠키가 남아 있으면 새로고침 때 다시 로그인되지만,
 * 사용자가 로그아웃을 눌렀는데 로그인 상태로 남아 있는 쪽이 더 나쁘다.
 */
export async function logout(): Promise<void> {
  try {
    await request<void>('/api/v1/auth/logout', { method: 'POST' })
  } finally {
    clearSession()
  }
}
