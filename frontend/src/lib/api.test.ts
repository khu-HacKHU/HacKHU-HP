import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import {
  clearSession,
  getSession,
  onSessionEvent,
  setSession,
  type SessionEvent,
} from './session'
import { AccountChangedError, ApiError, request } from './api'

function json(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

const userA = {
  id: 7,
  email: 'a@example.com',
  name: 'A',
  role: 'MEMBER' as const,
  mustChangePassword: false,
}
const userB = { ...userA, id: 9, email: 'b@example.com', name: 'B' }

function tokenFor(user: typeof userA, accessToken: string) {
  return { accessToken, tokenType: 'Bearer', expiresIn: 900, user }
}

/** 요청 공통 처리가 화면을 어디로 보내라고 알렸는지 모아 둔다 (auth 4-2-1). */
let events: SessionEvent[] = []
let unsubscribe: () => void = () => {}

beforeEach(() => {
  clearSession()
  events = []
  unsubscribe = onSessionEvent((event) => events.push(event))
})

afterEach(() => {
  unsubscribe()
  vi.restoreAllMocks()
  clearSession()
})

test('모든 요청에 credentials include를 붙인다', async () => {
  const fetchMock = vi.fn().mockResolvedValue(json({ ok: true }))
  vi.stubGlobal('fetch', fetchMock)

  await request('/api/v1/albums')

  expect(fetchMock.mock.calls[0][1].credentials).toBe('include')
})

test('세션이 있으면 Authorization Bearer 헤더를 붙인다', async () => {
  setSession({ user: userA, accessToken: 'token-1' })
  const fetchMock = vi.fn().mockResolvedValue(json({ ok: true }))
  vi.stubGlobal('fetch', fetchMock)

  await request('/api/v1/users/me')

  const headers = fetchMock.mock.calls[0][1].headers as Headers
  expect(headers.get('Authorization')).toBe('Bearer token-1')
})

test('토큰을 쿼리나 본문으로 보내지 않는다', async () => {
  setSession({ user: userA, accessToken: 'token-1' })
  const fetchMock = vi.fn().mockResolvedValue(json({ ok: true }))
  vi.stubGlobal('fetch', fetchMock)

  await request('/api/v1/posts', { method: 'POST', body: { title: '글' } })

  const [url, init] = fetchMock.mock.calls[0]
  expect(String(url)).not.toContain('token-1')
  expect(String(init.body)).not.toContain('token-1')
})

test('401을 받으면 갱신하고 원래 요청을 한 번 다시 보낸다', async () => {
  setSession({ user: userA, accessToken: 'old' })
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(json({ code: 'UNAUTHORIZED' }, 401))
    .mockResolvedValueOnce(json(tokenFor(userA, 'new')))
    .mockResolvedValueOnce(json({ ok: true }))
  vi.stubGlobal('fetch', fetchMock)

  await expect(request('/api/v1/users/me')).resolves.toEqual({ ok: true })

  expect(fetchMock).toHaveBeenCalledTimes(3)
  expect(String(fetchMock.mock.calls[1][0])).toBe('/api/v1/auth/refresh')
  // 다시 보낸 요청은 새 토큰을 쓴다.
  const retryHeaders = fetchMock.mock.calls[2][1].headers as Headers
  expect(retryHeaders.get('Authorization')).toBe('Bearer new')
  expect(getSession()?.accessToken).toBe('new')
})

test('갱신도 401이면 세션을 비우고 로그인 화면으로 보낸다', async () => {
  setSession({ user: userA, accessToken: 'old' })
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      // 원래 요청, 갱신, 토큰 없이 보낸 재시도가 모두 401이다. 로그인해야 쓸 수 있는 API다.
      .mockResolvedValue(json({ code: 'UNAUTHORIZED' }, 401)),
  )

  await expect(request('/api/v1/users/me')).rejects.toBeInstanceOf(ApiError)
  expect(getSession()).toBeNull()
  // 세션만 비우면 화면은 그대로 남는다. 계약은 로그인 화면으로 보내라고 한다 (4-2-1).
  expect(events).toEqual(['expired'])
})

test('갱신이 401이면 토큰 없이 한 번 더 보낸다', async () => {
  // 다른 탭에서 로그아웃한 뒤 공개 API를 부른 상황. 서버는 유효하지 않은 토큰이 붙은 공개 API도
  // 401로 거절하므로(auth 5-3), 토큰을 떼고 다시 보내면 공지와 갤러리는 그대로 보인다.
  setSession({ user: userA, accessToken: 'dead' })
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(json({ code: 'UNAUTHORIZED' }, 401))
    .mockResolvedValueOnce(json({ code: 'UNAUTHORIZED' }, 401))
    .mockResolvedValueOnce(json({ content: [] }))
  vi.stubGlobal('fetch', fetchMock)

  await expect(request('/api/v1/albums')).resolves.toEqual({ content: [] })

  const retryHeaders = fetchMock.mock.calls[2][1].headers as Headers
  expect(retryHeaders.get('Authorization')).toBeNull()
  expect(getSession()).toBeNull()
  // 공개 화면을 보던 사람을 로그인 화면으로 끌고 가지 않는다.
  expect(events).toEqual([])
})

test('토큰이 없던 요청은 토큰 없이 다시 보내지 않는다', async () => {
  // 같은 요청을 한 번 더 보내 봐야 결과가 같다.
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(json({ code: 'UNAUTHORIZED' }, 401)),
  )

  await expect(request('/api/v1/users/me')).rejects.toBeInstanceOf(ApiError)

  expect(globalThis.fetch).toHaveBeenCalledTimes(2)
  expect(events).toEqual(['expired'])
})

test('갱신이 서버 오류로 끝나면 세션을 비우지 않는다', async () => {
  // 500은 "세션이 끝났다"가 아니라 "확인하지 못했다"다. 비우면 잠깐의 오류로 로그아웃된다.
  setSession({ user: userA, accessToken: 'old' })
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(json({ code: 'UNAUTHORIZED' }, 401))
    .mockResolvedValueOnce(json({ code: 'INTERNAL_ERROR' }, 500))
  vi.stubGlobal('fetch', fetchMock)

  await expect(request('/api/v1/users/me')).rejects.toBeInstanceOf(ApiError)

  expect(fetchMock).toHaveBeenCalledTimes(2)
  expect(getSession()?.accessToken).toBe('old')
  expect(events).toEqual([])
})

test('갱신이 네트워크 오류로 끝나도 세션을 비우지 않는다', async () => {
  setSession({ user: userA, accessToken: 'old' })
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(json({ code: 'UNAUTHORIZED' }, 401))
      .mockRejectedValueOnce(new TypeError('Failed to fetch')),
  )

  await expect(request('/api/v1/users/me')).rejects.toBeInstanceOf(ApiError)

  expect(getSession()?.accessToken).toBe('old')
  expect(events).toEqual([])
})

test('비밀번호 변경이 필요하면 변경 화면으로 보내라고 알린다', async () => {
  // 로그인할 때만 확인하면 새로고침한 사용자는 원래 화면에 남아 모든 요청이 403으로 막힌다.
  setSession({ user: userA, accessToken: 'token-1' })
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValue(json({ code: 'AUTH_PASSWORD_CHANGE_REQUIRED' }, 403)),
  )

  await expect(request('/api/v1/albums')).rejects.toBeInstanceOf(ApiError)

  expect(events).toEqual(['password-change-required'])
  // 403은 로그인 상태가 끝난 것이 아니다.
  expect(getSession()?.accessToken).toBe('token-1')
})

test('역할이 부족한 403은 화면을 옮기지 않는다', async () => {
  setSession({ user: userA, accessToken: 'token-1' })
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(json({ code: 'FORBIDDEN' }, 403)),
  )

  await expect(request('/api/v1/admin/users')).rejects.toBeInstanceOf(ApiError)

  expect(events).toEqual([])
})

test('다시 보낸 요청이 또 401이면 갱신을 다시 부르지 않는다', async () => {
  setSession({ user: userA, accessToken: 'old' })
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(json({ code: 'UNAUTHORIZED' }, 401))
    .mockResolvedValueOnce(json(tokenFor(userA, 'new')))
    .mockResolvedValueOnce(json({ code: 'UNAUTHORIZED' }, 401))
  vi.stubGlobal('fetch', fetchMock)

  await expect(request('/api/v1/users/me')).rejects.toBeInstanceOf(ApiError)

  // 원래 요청, 갱신, 재시도까지 세 번. 갱신이 두 번 불리지 않는다.
  expect(fetchMock).toHaveBeenCalledTimes(3)
  expect(getSession()).toBeNull()
  expect(events).toEqual(['expired'])
})

test('갱신 결과가 다른 사용자면 원래 요청을 다시 보내지 않는다', async () => {
  // 다른 탭에서 B로 로그인해 refresh 쿠키가 B의 것으로 덮어써진 상황 (auth 4-2-1, CWE-287).
  setSession({ user: userA, accessToken: 'old' })
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(json({ code: 'UNAUTHORIZED' }, 401))
    .mockResolvedValueOnce(json(tokenFor(userB, 'b-token')))
  vi.stubGlobal('fetch', fetchMock)

  await expect(request('/api/v1/users/me')).rejects.toBeInstanceOf(
    AccountChangedError,
  )

  // A의 요청이 B의 토큰으로 다시 나가지 않는다. 그러면 A가 쓴 글이 B 명의로 저장된다.
  expect(fetchMock).toHaveBeenCalledTimes(2)
  // 탭의 현재 사용자는 B로 바뀐다.
  expect(getSession()?.user.id).toBe(userB.id)
})

test('인증 경로의 401에는 갱신을 시도하지 않는다', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(json({ code: 'AUTH_INVALID_CREDENTIALS' }, 401))
  vi.stubGlobal('fetch', fetchMock)

  await expect(
    request('/api/v1/auth/login', { method: 'POST', body: {} }),
  ).rejects.toBeInstanceOf(ApiError)

  // 로그인 실패에 갱신을 시도하면 무한히 돌 수 있다.
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

test('동시에 401을 받아도 갱신은 한 번만 부른다', async () => {
  setSession({ user: userA, accessToken: 'old' })
  const fetchMock = vi.fn((url: string) => {
    if (String(url) === '/api/v1/auth/refresh') {
      return Promise.resolve(json(tokenFor(userA, 'new')))
    }
    // 첫 호출은 401, 그 뒤로는 성공.
    const seen = fetchMock.mock.calls.filter(
      (call) => String(call[0]) !== '/api/v1/auth/refresh',
    ).length
    return Promise.resolve(seen <= 2 ? json({}, 401) : json({ ok: true }))
  })
  vi.stubGlobal('fetch', fetchMock)

  await Promise.allSettled([
    request('/api/v1/a'),
    request('/api/v1/b'),
    request('/api/v1/c'),
  ])

  const refreshCalls = fetchMock.mock.calls.filter(
    (call) => String(call[0]) === '/api/v1/auth/refresh',
  )
  // 갱신은 토큰을 회전시킨다. 동시에 불러도 10초 안이면 계약이 받아 주지만(4-2-5 두 번째 줄),
  // 요청 수만큼 쿠키가 회전하고 늦게 처리된 요청이 10초를 넘기면 재사용으로 판정된다.
  expect(refreshCalls).toHaveLength(1)
})

test('429는 Retry-After 초를 담는다', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValue(
        json({ code: 'TOO_MANY_REQUESTS' }, 429, { 'Retry-After': '42' }),
      ),
  )

  const caught = await request('/api/v1/auth/login', {
    method: 'POST',
    body: {},
  }).catch((error: unknown) => error)

  expect(caught).toBeInstanceOf(ApiError)
  expect((caught as ApiError).retryAfterSeconds).toBe(42)
})

test('400의 필드별 사유를 꺼낼 수 있다', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      json(
        {
          code: 'INVALID_INPUT',
          message: '입력값이 올바르지 않습니다.',
          errors: [{ field: 'email', reason: '이메일 형식이 아닙니다.' }],
        },
        400,
      ),
    ),
  )

  const caught = (await request('/api/v1/auth/signup', {
    method: 'POST',
    body: {},
  }).catch((error: unknown) => error)) as ApiError

  expect(caught.reasonFor('email')).toBe('이메일 형식이 아닙니다.')
  expect(caught.reasonFor('password')).toBeNull()
})

test('JSON이 아닌 오류 응답도 ApiError로 바꾼다', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValue(new Response('<html>502</html>', { status: 502 })),
  )

  const caught = (await request('/api/v1/albums').catch(
    (error: unknown) => error,
  )) as ApiError

  expect(caught).toBeInstanceOf(ApiError)
  expect(caught.status).toBe(502)
  expect(caught.code).toBe('UNKNOWN')
})

test('204는 본문을 읽지 않는다', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
  )

  await expect(
    request('/api/v1/auth/logout', { method: 'POST' }),
  ).resolves.toBeUndefined()
})
