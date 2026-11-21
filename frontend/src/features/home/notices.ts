// 메인 화면이 쓰는 공지 목록 계약 (board backend 4-2-3)
// 공지 목록 API(#29, PR #113)가 머지되기 전이라 테스트는 가짜 응답으로 한다.
// 게시판 프론트가 생기면 그쪽 API 함수로 옮긴다.

export type NoticeSummary = {
  id: number
  title: string
  author: { id: number; name: string }
  createdAt: string
  updatedAt: string
}

export const RECENT_NOTICE_COUNT = 5

// 최근 공지: GET /api/v1/notices?page=0&size=5 (정렬은 서버가 작성 시각 최신순으로 고정)
export async function fetchRecentNotices(): Promise<NoticeSummary[]> {
  const params = new URLSearchParams({
    page: '0',
    size: String(RECENT_NOTICE_COUNT),
  })
  const res = await fetch(`/api/v1/notices?${params}`)
  if (!res.ok) throw new Error(`공지 목록 요청 실패: ${res.status}`)
  const body: { content: NoticeSummary[] } = await res.json()
  return body.content
}

// 작성 시각(UTC)을 한국 날짜 YYYY-MM-DD로
const KOREA_DATE = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Seoul',
})

export function toKoreanDate(iso: string): string {
  return KOREA_DATE.format(new Date(iso))
}
