import { ApiError, toApiError } from './api'
import type { AlbumDetail } from './types'
import { resolveContentType } from './validation'

// 앨범 올리기 흐름 (backend 4-2 3절, 4절)
//   1. POST /api/v1/albums/upload-urls 로 사진마다 업로드 주소를 받는다
//   2. 받은 주소에 사진을 PUT 한다 (S3 presigned PUT, 서버를 거치지 않음)
//   3. POST /api/v1/albums 로 uploadId 목록을 넘겨 앨범을 만든다
//
// 인증 헤더는 아직 붙이지 않는다. 토큰 보관 위치가 정해지면(#26) 붙인다 (frontend/AGENTS.md, CWE-922).
// 그 전까지 실제 서버는 401을 준다.

export type UploadSlot = {
  uploadId: string
  uploadUrl: string
  expiresAt: string
}

export type CreateAlbumRequest = {
  title: string
  eventDate: string
  uploadIds: string[] // 배열 순서 = 사진 순서, 0번째 = 대표 사진
}

// S3 업로드 실패. 서버 API 오류(ApiError)와 구분한다
export class StorageUploadError extends Error {}

// 1. 업로드 주소 발급: POST /api/v1/albums/upload-urls
export async function requestUploadUrls(files: File[]): Promise<UploadSlot[]> {
  const res = await fetch('/api/v1/albums/upload-urls', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      files: files.map((file) => ({
        contentType: resolveContentType(file),
        size: file.size,
      })),
    }),
  })
  if (!res.ok) throw await toApiError(res)
  const body: { uploads: UploadSlot[] } = await res.json()
  if (body.uploads.length !== files.length) {
    throw new ApiError(res.status, 'UNKNOWN', '')
  }
  return body.uploads
}

// 2. 사진 한 장을 발급받은 주소로 올린다.
// Content-Length는 브라우저가 파일 크기로 채운다. 발급 때 적은 size와 같아야 S3가 받아 준다.
export async function putPhoto(slot: UploadSlot, file: File): Promise<void> {
  let res: Response
  try {
    res = await fetch(slot.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': resolveContentType(file) ?? '' },
      body: file,
    })
  } catch {
    throw new StorageUploadError()
  }
  // S3 오류 본문은 XML이라 읽지 않는다
  if (!res.ok) throw new StorageUploadError()
}

// 3. 앨범 만들기: POST /api/v1/albums. 성공하면 상세와 같은 형태를 준다
export async function createAlbum(
  request: CreateAlbumRequest,
): Promise<AlbumDetail> {
  const res = await fetch('/api/v1/albums', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!res.ok) throw await toApiError(res)
  return res.json()
}

// 1~2단계: 주소를 받고 사진을 모두 올린 뒤, 3단계에 넘길 요청을 돌려준다.
// 3단계를 따로 두는 이유: 서버가 500을 주면 같은 uploadId로 다시 시도할 수 있다 (4-2 4절 4번)
export async function uploadPhotos(
  title: string,
  eventDate: string,
  photos: File[],
): Promise<CreateAlbumRequest> {
  const slots = await requestUploadUrls(photos)
  await Promise.all(slots.map((slot, i) => putPhoto(slot, photos[i])))
  return { title, eventDate, uploadIds: slots.map((slot) => slot.uploadId) }
}
