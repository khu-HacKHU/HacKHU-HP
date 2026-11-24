/*
 * 모든 API 호출이 지나는 곳 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-1).
 *
 * 기능별 api.ts는 여기의 request를 쓴다. 컴포넌트에서 fetch를 직접 부르지 않는다
 * (frontend/AGENTS.md).
 */
import {
  clearSession,
  emitSessionEvent,
  getSession,
  replaceSession,
  type AuthUser,
} from './session'

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
 * 갱신 결과. **401과 그 밖의 실패를 구분한다.**
 *
 * <ul>
 *   <li>`ok`: 새 토큰을 받았다.
 *   <li>`expired`: 서버가 401을 줬다. 쿠키가 없거나 세션이 폐기되었다. 로그인 상태가 끝났다.
 *   <li>`failed`: 서버 오류나 네트워크 문제로 확인하지 못했다. 세션이 끝났는지는 알 수 없다.
 * </ul>
 */
export type RefreshResult =
  | { status: 'ok'; token: TokenResponse }
  | { status: 'expired' }
  | { status: 'failed' }

/**
 * 갱신 요청은 한 번에 하나만 보낸다. 여러 요청이 동시에 401을 받아도 refresh는 한 번만 부른다.
 *
 * <p>계약은 회전한 지 10초 안의 토큰을 동시 요청으로 보고 받아 주므로(4-2-5 두 번째 줄) 동시에
 * 불러도 곧바로 끊기지는 않는다. 다만 요청 수만큼 쿠키가 회전해 어느 응답의 쿠키가 남을지가
 * 순서에 달리고, 같은 세션의 갱신은 세션 행을 잠가 하나씩 처리되므로 늦게 처리된 요청이 10초를
 * 넘기면 재사용으로 판정된다(네 번째 줄). 하나로 모으면 그 창을 아예 건드리지 않는다.
 */
let inFlightRefresh: Promise<RefreshResult> | null = null

async function callRefresh(): Promise<RefreshResult> {
  let res: Response
  try {
    res = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'include',
    })
  } catch {
    // 네트워크 오류. 세션이 끝났다는 뜻이 아니다.
    return { status: 'failed' }
  }
  if (res.status === 401) return { status: 'expired' }
  if (!res.ok) return { status: 'failed' }
  try {
    return { status: 'ok', token: (await res.json()) as TokenResponse }
  } catch {
    return { status: 'failed' }
  }
}

/** 갱신을 한 번만 보내고 결과를 같이 쓴다. 시간 초과로 중간에 끊지 않는다 (4-2-1). */
export function refreshSession(): Promise<RefreshResult> {
  if (inFlightRefresh === null) {
    inFlightRefresh = callRefresh().finally(() => {
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
 *   <li>갱신까지 401이면 세션을 비우고 로그인 화면으로 보낸다 (4-2-1). 갱신이 서버 오류나
 *       네트워크 문제로 끝났으면 세션을 비우지 않는다.
 * </ul>
 *
 * @throws ApiError 서버가 실패를 돌려줌
 * @throws AccountChangedError 갱신 결과의 사용자가 달라 요청을 버림
 */
export async function request<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const before = getSession()
  const requestedBy = before?.user.id ?? null

  const first = await send(path, options)
  if (first.status !== 401 || isAuthPath(path)) {
    return finish<T>(first)
  }

  const refreshed = await refreshSession()

  // 갱신이 500이나 네트워크 오류로 끝났다. 세션이 끝났는지 알 수 없으므로 비우지 않는다.
  // 비우면 잠깐의 오류로 로그아웃되고, 쓰던 입력이 있는 화면에서 다시 로그인해야 한다.
  if (refreshed.status === 'failed') {
    throw await toApiError(first)
  }

  if (refreshed.status === 'expired') {
    clearSession()
    // 토큰을 실었던 요청만 토큰 없이 한 번 더 보낸다. 공개 API도 유효하지 않은 토큰이 붙으면
    // 401이라(auth 5-3), 다른 탭에서 로그아웃한 뒤 공지와 갤러리가 오류로 끝난다.
    if (before !== null) {
      const anonymous = await send(path, options)
      if (anonymous.status !== 401) return finish<T>(anonymous)
    }
    // 토큰 없이는 쓸 수 없는 API다. 계약대로 로그인 화면으로 보낸다 (4-2-1).
    emitSessionEvent('expired')
    throw await toApiError(first)
  }

  const token = refreshed.token

  // 원래 요청을 만든 사용자와 다른 사용자의 토큰을 받았으면 다시 보내지 않는다.
  // 그대로 보내면 A가 쓴 글이 B 명의로 저장되거나 A 화면에 B 데이터가 보인다 (4-2-1).
  if (requestedBy !== null && token.user.id !== requestedBy) {
    replaceSession(token.user, token.accessToken)
    throw new AccountChangedError(token.user)
  }

  replaceSession(token.user, token.accessToken)

  // 한 번만 다시 보낸다. 또 401이면 갱신을 다시 부르지 않는다 (4-2-1).
  const second = await send(path, options)
  if (second.status === 401) {
    clearSession()
    emitSessionEvent('expired')
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
  if (!res.ok) {
    const error = await toApiError(res)
    // 비밀번호를 바꿔야 하는 상태에서는 허용된 API 말고 전부 403이다. 어느 화면에서 걸렸든
    // 변경 화면으로 보낸다. 로그인할 때만 확인하면 새로고침 뒤에 걸러지지 않는다 (4-2-1).
    if (
      error.status === 403 &&
      error.code === 'AUTH_PASSWORD_CHANGE_REQUIRED'
    ) {
      emitSessionEvent('password-change-required')
    }
    throw error
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
