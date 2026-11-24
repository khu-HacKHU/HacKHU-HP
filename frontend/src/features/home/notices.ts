import { request } from '../../lib/api'

// 메인 화면이 쓰는 공지 목록 계약 (board backend 4-2-3)
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
  // 공통 처리(credentials, 토큰 헤더, 401 뒤 갱신, 오류 변환)는 request가 한다 (frontend/AGENTS.md)
  const body = await request<{ content: NoticeSummary[] }>(
    `/api/v1/notices?${params}`,
  )
  return body.content
}

// 작성 시각(UTC)을 한국 날짜 YYYY-MM-DD로
const KOREA_DATE = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Seoul',
})

export function toKoreanDate(iso: string): string {
  return KOREA_DATE.format(new Date(iso))
}
