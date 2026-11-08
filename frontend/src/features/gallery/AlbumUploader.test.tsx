import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { AlbumUploader } from './AlbumUploader'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function makeFile(name: string, type: string, size = 1000): File {
  const file = new File(['x'], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

const uploadsBody = {
  uploads: [
    {
      uploadId: 'u-1',
      uploadUrl: 'https://s3.example.com/1',
      expiresAt: '2026-11-08T10:10:00Z',
    },
    {
      uploadId: 'u-2',
      uploadUrl: 'https://s3.example.com/2',
      expiresAt: '2026-11-08T10:10:00Z',
    },
  ],
}

const createdBody = {
  id: 30,
  title: '세미나',
  eventDate: '2026-10-18',
  photos: [],
  createdAt: '2026-11-08T10:00:00Z',
}

// 요청 주소에 따라 응답을 돌려주는 가짜 fetch
function stubServer(handlers: {
  uploadUrls?: () => Response
  put?: () => Response
  create?: () => Response
}) {
  const fetchMock = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url === '/api/v1/albums/upload-urls') {
      return handlers.uploadUrls?.() ?? jsonResponse(uploadsBody)
    }
    if (url.startsWith('https://s3.example.com/')) {
      return handlers.put?.() ?? new Response(null, { status: 200 })
    }
    if (url === '/api/v1/albums') {
      return handlers.create?.() ?? jsonResponse(createdBody, 201)
    }
    throw new Error(`예상하지 못한 요청: ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const photos = [
  makeFile('a.jpg', 'image/jpeg', 2000),
  makeFile('b.png', 'image/png', 3000),
]

function fillAndSubmit() {
  fireEvent.change(screen.getByLabelText('제목'), {
    target: { value: '세미나' },
  })
  fireEvent.change(screen.getByLabelText('활동 날짜'), {
    target: { value: '2026-10-18' },
  })
  fireEvent.change(screen.getByLabelText(/사진/), {
    target: { files: photos },
  })
  fireEvent.click(screen.getByRole('button', { name: '올리기' }))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

test('주소 발급, 사진 PUT, 앨범 만들기를 차례로 하고 만든 앨범을 넘긴다', async () => {
  const fetchMock = stubServer({})
  const onUploaded = vi.fn()
  render(<AlbumUploader onUploaded={onUploaded} onCancel={vi.fn()} />)

  fillAndSubmit()

  await vi.waitFor(() => expect(onUploaded).toHaveBeenCalledWith(createdBody))

  // 1. 주소 발급에는 형식과 크기만 보낸다 (파일 이름은 보내지 않음)
  const [, issueInit] = fetchMock.mock.calls[0] as [string, RequestInit]
  expect(JSON.parse(issueInit.body as string)).toEqual({
    files: [
      { contentType: 'image/jpeg', size: 2000 },
      { contentType: 'image/png', size: 3000 },
    ],
  })

  // 2. 받은 주소마다 해당 사진을 PUT 한다
  const puts = fetchMock.mock.calls.filter(([url]) =>
    String(url).startsWith('https://s3.example.com/'),
  ) as [string, RequestInit][]
  expect(puts.map(([url, init]) => [url, init.method, init.body])).toEqual([
    ['https://s3.example.com/1', 'PUT', photos[0]],
    ['https://s3.example.com/2', 'PUT', photos[1]],
  ])

  // 3. 사진 순서대로 uploadId를 넘겨 앨범을 만든다
  const [, createInit] = fetchMock.mock.calls.at(-1) as [string, RequestInit]
  expect(JSON.parse(createInit.body as string)).toEqual({
    title: '세미나',
    eventDate: '2026-10-18',
    uploadIds: ['u-1', 'u-2'],
  })
})

test('동아리원이 아니면(403) 이유를 안내하고 사진을 올리지 않는다', async () => {
  const fetchMock = stubServer({
    uploadUrls: () =>
      jsonResponse(
        { status: 403, code: 'FORBIDDEN', message: '서버 문구', errors: [] },
        403,
      ),
  })
  render(<AlbumUploader onUploaded={vi.fn()} onCancel={vi.fn()} />)

  fillAndSubmit()

  expect((await screen.findByRole('alert')).textContent).toBe(
    '동아리원만 앨범을 올릴 수 있어요.',
  )
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

test('로그인하지 않았으면(401) 로그인이 필요하다고 안내한다', async () => {
  stubServer({
    uploadUrls: () =>
      jsonResponse({ status: 401, code: 'UNAUTHORIZED', message: '' }, 401),
  })
  render(<AlbumUploader onUploaded={vi.fn()} onCancel={vi.fn()} />)

  fillAndSubmit()

  expect((await screen.findByRole('alert')).textContent).toBe(
    '로그인이 필요해요.',
  )
})

test('S3에 사진을 못 올리면 앨범을 만들지 않고 안내한다', async () => {
  const fetchMock = stubServer({
    put: () => new Response(null, { status: 403 }),
  })
  const onUploaded = vi.fn()
  render(<AlbumUploader onUploaded={onUploaded} onCancel={vi.fn()} />)

  fillAndSubmit()

  expect((await screen.findByRole('alert')).textContent).toBe(
    '사진을 올리지 못했어요. 잠시 후 다시 시도해 주세요.',
  )
  expect(fetchMock.mock.calls.some(([url]) => url === '/api/v1/albums')).toBe(
    false,
  )
  expect(onUploaded).not.toHaveBeenCalled()
})

test('사진 확인에 실패하면(GALLERY_INVALID_UPLOAD) 다시 고르라고 안내한다', async () => {
  stubServer({
    create: () =>
      jsonResponse(
        {
          status: 400,
          code: 'GALLERY_INVALID_UPLOAD',
          message: '',
          errors: [],
        },
        400,
      ),
  })
  render(<AlbumUploader onUploaded={vi.fn()} onCancel={vi.fn()} />)

  fillAndSubmit()

  expect((await screen.findByRole('alert')).textContent).toBe(
    '올린 사진을 확인할 수 없어요. 사진을 다시 골라 올려 주세요.',
  )
  // 클라이언트 잘못이면 같은 uploadId로 다시 시도할 수 없다
  expect(screen.queryByRole('button', { name: '다시 시도' })).toBeNull()
})

test('앨범 만들기에서 서버 오류가 나면 사진을 다시 올리지 않고 다시 시도한다', async () => {
  let createCalls = 0
  const fetchMock = stubServer({
    create: () => {
      createCalls += 1
      return createCalls === 1
        ? jsonResponse(
            { status: 500, code: 'INTERNAL_ERROR', message: '' },
            500,
          )
        : jsonResponse(createdBody, 201)
    },
  })
  const onUploaded = vi.fn()
  render(<AlbumUploader onUploaded={onUploaded} onCancel={vi.fn()} />)

  fillAndSubmit()
  fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }))

  await vi.waitFor(() => expect(onUploaded).toHaveBeenCalledWith(createdBody))
  // 주소 발급 1번, PUT 2번, 앨범 만들기 2번
  expect(fetchMock).toHaveBeenCalledTimes(5)
  const [, retryInit] = fetchMock.mock.calls.at(-1) as [string, RequestInit]
  expect(JSON.parse(retryInit.body as string).uploadIds).toEqual(['u-1', 'u-2'])
})
