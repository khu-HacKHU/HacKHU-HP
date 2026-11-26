import { render, screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import { ClubIntroPage } from './ClubIntroPage'
import { CONTACTS } from './clubIntro'

test('소개, 활동, 문의 영역이 모두 보인다', () => {
  render(<ClubIntroPage />)

  expect(screen.getByRole('heading', { name: 'HacKHU 소개' })).toBeDefined()
  expect(screen.getByRole('heading', { name: '소개' })).toBeDefined()
  expect(screen.getByRole('heading', { name: '활동' })).toBeDefined()
  expect(screen.getByRole('heading', { name: '문의' })).toBeDefined()
})

test('경희대학교 최초의 정보보안 동아리라는 소개와 환영 문구가 보인다', () => {
  render(<ClubIntroPage />)

  expect(screen.getByText(/경희대학교 최초의 정보보안 동아리/)).toBeDefined()
  expect(screen.getByText(/전공, 학년, 경험과 관계없이.*환영/)).toBeDefined()
})

test('정기 스터디, 팀 프로젝트, 연합 CTF 활동이 순서대로 보인다', () => {
  render(<ClubIntroPage />)

  const activities = screen.getByRole('region', { name: '활동' })
  const names = within(activities)
    .getAllByRole('listitem')
    .map((item) => item.querySelector('strong')?.textContent)
  expect(names).toEqual(['정기 스터디', '팀 프로젝트', '연합 CTF'])
})

test('연락처가 정해지지 않았으면 준비 중이라고 안내한다', () => {
  render(<ClubIntroPage contacts={[]} />)

  const contact = screen.getByRole('region', { name: '문의' })
  expect(within(contact).getByText('연락처를 준비하고 있어요.')).toBeDefined()
})

test('이메일은 메일 링크로, SNS는 새 탭에서 안전하게 열리는 링크로 보인다', () => {
  render(
    <ClubIntroPage
      contacts={[
        { kind: 'email', label: '이메일', address: 'club@example.com' },
        { kind: 'link', label: '인스타그램', url: 'https://example.com/club' },
      ]}
    />,
  )

  const email = screen.getByRole('link', { name: 'club@example.com' })
  expect(email.getAttribute('href')).toBe('mailto:club@example.com')

  const sns = screen.getByRole('link', { name: '인스타그램' })
  expect(sns.getAttribute('href')).toBe('https://example.com/club')
  expect(sns.getAttribute('target')).toBe('_blank')
  expect(sns.getAttribute('rel')).toBe('noopener noreferrer')
})

test('https가 아닌 주소는 링크로 만들지 않는다', () => {
  render(
    <ClubIntroPage
      contacts={[
        { kind: 'link', label: '이상한 링크', url: 'javascript:alert(1)' },
      ]}
    />,
  )

  expect(screen.queryByRole('link')).toBeNull()
  expect(screen.getByText('이상한 링크')).toBeDefined()
})

// 공개 화면이라 개인 휴대전화 번호가 들어가면 안 된다 (USER-STORIES 2절)
test('연락처에 휴대전화 번호 형식이 없다', () => {
  const phone = /01[016789][-\s.]?\d{3,4}[-\s.]?\d{4}/
  for (const contact of CONTACTS) {
    const text = contact.kind === 'email' ? contact.address : contact.url
    expect(text).not.toMatch(phone)
    expect(contact.label).not.toMatch(phone)
  }
})
