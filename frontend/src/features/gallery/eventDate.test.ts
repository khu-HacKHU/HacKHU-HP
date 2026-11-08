import { expect, test } from 'vitest'
import { todayString, validateEventDate } from './validation'

const now = new Date(2026, 10, 8, 23, 30) // 2026-11-08 23:30 (기기 시간)

test('오늘 날짜를 YYYY-MM-DD로 만든다', () => {
  expect(todayString(now)).toBe('2026-11-08')
})

test('오늘까지는 통과하고 내일부터는 막는다', () => {
  expect(validateEventDate('2026-11-08', now)).toBeNull()
  expect(validateEventDate('2026-11-09', now)).toBe(
    '활동 날짜는 오늘까지만 고를 수 있어요.',
  )
})
