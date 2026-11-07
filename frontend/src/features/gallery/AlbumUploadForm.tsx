import { useState } from 'react'
import type { FormEvent } from 'react'
import { validateEventDate, validatePhotos, validateTitle } from './validation'

// 폼이 부모에게 넘기는 값
export type AlbumUploadInput = {
  title: string
  eventDate: string
  photos: File[]
}

type AlbumUploadFormProps = {
  onSubmit: (input: AlbumUploadInput) => void
}

// 앨범 올리기 폼. 입력을 검사하고, 통과하면 onSubmit으로 넘긴다.
// 실제 업로드(서버 호출)는 이 컴포넌트가 하지 않는다.
export function AlbumUploadForm({ onSubmit }: AlbumUploadFormProps) {
  const [title, setTitle] = useState('')
  const [eventDate, setEventDate] = useState('')
  const [photos, setPhotos] = useState<File[]>([])
  const [error, setError] = useState<string | null>(null)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault() // 브라우저가 페이지를 새로고침하지 않게 막는다
    const message =
      validateTitle(title) ??
      validateEventDate(eventDate) ??
      validatePhotos(photos)
    if (message) {
      setError(message)
      return
    }
    setError(null)
    onSubmit({ title: title.trim(), eventDate, photos })
  }

  return (
    <form onSubmit={handleSubmit}>
      <label>
        제목
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
      </label>
      <label>
        활동 날짜
        <input
          type="date"
          value={eventDate}
          onChange={(e) => setEventDate(e.target.value)}
        />
      </label>
      <label>
        사진 (jpg, png, webp, 한 장 10MB 이하, 최대 20장)
        <input
          type="file"
          multiple
          accept=".jpg,.jpeg,.png,.webp"
          onChange={(e) => setPhotos(Array.from(e.target.files ?? []))}
        />
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit">올리기</button>
    </form>
  )
}
