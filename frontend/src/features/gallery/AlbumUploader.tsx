import { useState } from 'react'
import { AlbumUploadForm } from './AlbumUploadForm'
import type { AlbumUploadInput } from './AlbumUploadForm'
import { createAlbum, uploadPhotos } from './upload'
import type { CreateAlbumRequest } from './upload'
import { canRetryCreate, uploadErrorMessage } from './uploadErrors'
import type { AlbumDetail } from './types'

type AlbumUploaderProps = {
  onUploaded: (album: AlbumDetail) => void
  onCancel: () => void
}

// 앨범 올리기 화면: 폼 입력을 받아 실제로 올린다
export function AlbumUploader({ onUploaded, onCancel }: AlbumUploaderProps) {
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // 사진은 다 올렸는데 앨범 만들기에서 서버 오류가 났을 때, 다시 시도할 요청
  const [pending, setPending] = useState<CreateAlbumRequest | null>(null)

  async function finish(request: CreateAlbumRequest) {
    try {
      onUploaded(await createAlbum(request))
    } catch (e) {
      setError(uploadErrorMessage(e))
      setPending(canRetryCreate(e) ? request : null)
    }
  }

  async function handleSubmit(input: AlbumUploadInput) {
    setUploading(true)
    setError(null)
    setPending(null)
    try {
      const request = await uploadPhotos(
        input.title,
        input.eventDate,
        input.photos,
      )
      await finish(request)
    } catch (e) {
      setError(uploadErrorMessage(e))
    } finally {
      setUploading(false)
    }
  }

  async function retryCreate() {
    if (!pending) return
    setUploading(true)
    setError(null)
    try {
      await finish(pending)
    } finally {
      setUploading(false)
    }
  }

  return (
    <section>
      <h2>앨범 올리기</h2>
      <AlbumUploadForm onSubmit={handleSubmit} disabled={uploading} />
      {uploading && <p>올리는 중...</p>}
      {error && <p role="alert">{error}</p>}
      {pending && !uploading && (
        <button type="button" onClick={retryCreate}>
          다시 시도
        </button>
      )}
      <button type="button" onClick={onCancel} disabled={uploading}>
        취소
      </button>
    </section>
  )
}
