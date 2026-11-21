import { expect, test } from 'vitest'
import { todayString, validateEventDate } from './validation'

// 시각은 UTC로 정해둔다.
const now = new Date(Date.UTC(2026, 10, 8, 14, 30)) // 한국 2026-11-08 23:30

test('오늘 날짜를 YYYY-MM-DD로 만든다', () => {
  expect(todayString(now)).toBe('2026-11-08')
})

test('오늘까지는 통과하고 내일부터는 막는다', () => {
  expect(validateEventDate('2026-11-08', now)).toBeNull()
  expect(validateEventDate('2026-11-09', now)).toBe(
    '활동 날짜는 오늘까지만 고를 수 있어요.',
  )
})

test('UTC로는 전날이어도 한국 날짜를 쓴다', () => {
  const koreaDawn = new Date(Date.UTC(2026, 10, 8, 18, 30)) // 한국 11-09 03:30, UTC 11-08
  expect(todayString(koreaDawn)).toBe('2026-11-09')
  expect(validateEventDate('2026-11-09', koreaDawn)).toBeNull()
})

test('한국 자정 직전과 직후를 나눈다', () => {
  const beforeMidnight = new Date(Date.UTC(2026, 10, 8, 14, 59, 59)) // 한국 11-08 23:59:59
  const atMidnight = new Date(Date.UTC(2026, 10, 8, 15, 0, 0)) // 한국 11-09 00:00:00
  expect(todayString(beforeMidnight)).toBe('2026-11-08')
  expect(todayString(atMidnight)).toBe('2026-11-09')
})
