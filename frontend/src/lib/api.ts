/*
 * 모든 API 호출이 지나는 곳 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-1).
 *
 * 기능별 api.ts는 여기의 request를 쓴다. 컴포넌트에서 fetch를 직접 부르지 않는다
 * (frontend/AGENTS.md).
 */
import {
  clearSession,
  getSession,
  replaceSession,
  type AuthUser,
} from '../features/auth/session'

/** 400일 때 어느 항목이 왜 틀렸는지 (docs/conventions/api.md 3절). */
export type FieldError = { field: string; reason: string }

/** API가 돌려준 실패. code는 API 규약의 오류 코드다 (docs/conventions/api.md 3절). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  /** 429일 때 Retry-After 헤더의 초. 없으면 null (4-2-1). */
  readonly retryAfterSeconds: number | null
  readonly fieldErrors: readonly FieldError[]

  constructor(
    status: number,
    code: string,
    message: string,
    retryAfterSeconds: number | null = null,
    fieldErrors: readonly FieldError[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
    this.fieldErrors = fieldErrors
  }

  /** 그 항목의 첫 사유. 없으면 null. */
  reasonFor(field: string): string | null {
    return this.fieldErrors.find((it) => it.field === field)?.reason ?? null
  }
}

function toFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return []
  return value.filter(
    (it): it is FieldError =>
      typeof it?.field === 'string' && typeof it?.reason === 'string',
  )
}

async function toApiError(res: Response): Promise<ApiError> {
  const header = res.headers.get('Retry-After')
  const retryAfter =
    header !== null && /^\d+$/.test(header) ? Number(header) : null
  try {
    const body = await res.json()
    return new ApiError(
      res.status,
      typeof body?.code === 'string' ? body.code : 'UNKNOWN',
      typeof body?.message === 'string' ? body.message : '',
      retryAfter,
      toFieldErrors(body?.errors),
    )
  } catch {
    // 본문이 없거나 JSON이 아닌 응답(502 등)도 같은 모양으로 다룬다.
    return new ApiError(res.status, 'UNKNOWN', '', retryAfter)
  }
}

/** refresh 응답. 로그인 응답과 같은 모양이다 (4-2-5). */
type TokenResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

/** access 토큰을 쓰지 않는 경로. 이 경로의 401에는 갱신을 시도하지 않는다 (4-2-1). */
function isAuthPath(path: string): boolean {
  return path.startsWith('/api/v1/auth/')
}

/**
 * 갱신 요청은 한 번에 하나만 보낸다. 여러 요청이 동시에 401을 받아도 refresh는 한 번만 부른다.
 * 토큰을 회전시키는 API라 동시에 부르면 뒤에 도착한 요청이 재사용으로 판정되어 세션이 끊긴다
 * (4-2-5).
 */
let inFlightRefresh: Promise<TokenResponse | null> | null = null

async function callRefresh(): Promise<TokenResponse | null> {
  const res = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    credentials: 'include',
  })
  if (!res.ok) return null
  return (await res.json()) as TokenResponse
}

/** 갱신을 한 번만 보내고 결과를 같이 쓴다. 시간 초과로 중간에 끊지 않는다 (4-2-1). */
export function refreshSession(): Promise<TokenResponse | null> {
  if (inFlightRefresh === null) {
    inFlightRefresh = callRefresh()
      .catch(() => null)
      .finally(() => {
        inFlightRefresh = null
      })
  }
  return inFlightRefresh
}

export type RequestOptions = {
  method?: string
  /** JSON으로 직렬화해 보낸다. Content-Type은 request가 붙인다. */
  body?: unknown
}

/**
 * API를 부른다.
 *
 * <ul>
 *   <li>모든 요청에 {@code credentials: 'include'}를 붙인다. 쿠키는 Path가 /api/v1/auth라
 *       다른 API에는 붙여도 실리지 않는다.
 *   <li>세션이 있으면 {@code Authorization: Bearer}를 붙인다. 쿼리, 본문, 쿠키로 보내지 않는다.
 *   <li>401을 받으면 갱신을 한 번 시도하고 원래 요청을 한 번만 다시 보낸다.
 *   <li>갱신으로 받은 사용자가 요청을 만든 사용자와 다르면 **다시 보내지 않고** 던진다.
 *       다른 탭에서 계정이 바뀐 경우다 (CWE-287).
 * </ul>
 *
 * @throws ApiError 서버가 실패를 돌려줌
 * @throws AccountChangedError 갱신 결과의 사용자가 달라 요청을 버림
 */
export async function request<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const requestedBy = getSession()?.user.id ?? null

  const first = await send(path, options)
  if (first.status !== 401 || isAuthPath(path)) {
    return finish<T>(first)
  }

  const refreshed = await refreshSession()
  if (refreshed === null) {
    clearSession()
    throw await toApiError(first)
  }

  // 원래 요청을 만든 사용자와 다른 사용자의 토큰을 받았으면 다시 보내지 않는다.
  // 그대로 보내면 A가 쓴 글이 B 명의로 저장되거나 A 화면에 B 데이터가 보인다 (4-2-1).
  if (requestedBy !== null && refreshed.user.id !== requestedBy) {
    replaceSession(refreshed.user, refreshed.accessToken)
    throw new AccountChangedError(refreshed.user)
  }

  replaceSession(refreshed.user, refreshed.accessToken)

  // 한 번만 다시 보낸다. 또 401이면 갱신을 다시 부르지 않는다 (4-2-1).
  const second = await send(path, options)
  if (second.status === 401) {
    clearSession()
  }
  return finish<T>(second)
}

/** 갱신하는 동안 다른 탭에서 계정이 바뀌어 원래 요청을 버렸다 (4-2-1). */
export class AccountChangedError extends Error {
  readonly user: AuthUser

  constructor(user: AuthUser) {
    super('다른 탭에서 계정이 바뀌어 요청을 보내지 않았습니다.')
    this.name = 'AccountChangedError'
    this.user = user
  }
}

function send(path: string, options: RequestOptions): Promise<Response> {
  const headers = new Headers()
  const token = getSession()?.accessToken
  if (token !== undefined) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  return fetch(path, {
    method: options.method ?? 'GET',
    credentials: 'include',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
}

async function finish<T>(res: Response): Promise<T> {
  if (!res.ok) throw await toApiError(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
