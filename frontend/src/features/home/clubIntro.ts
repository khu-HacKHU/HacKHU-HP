// 동아리 소개 화면에 보여 줄 내용
// 화면에서는 고칠 수 없다

export type Activity = {
  name: string
  description: string
}

// 동아리 공용 연락처.(누구나 보는 화면이라 수집될 수 있음)
export type Contact =
  | { kind: 'email'; label: string; address: string }
  | { kind: 'link'; label: string; url: string } // SNS 등. https 주소만 넣는다

export const CLUB_NAME = 'HacKHU'

export const INTRO_PARAGRAPHS = [
  'HacKHU는 경희대학교 최초의 정보보안 동아리입니다.',
  '전공, 학년, 경험과 관계없이 보안에 관심 있는 사람이라면 누구나 환영합니다.',
]

export const ACTIVITIES: Activity[] = [
  {
    name: '정기 스터디',
    description: '매주 모여 보안 기초부터 함께 공부합니다.',
  },
  {
    name: '팀 프로젝트',
    description: 'CTF 출제, 버그 바운티 같은 실전 경험을 쌓습니다.',
  },
  {
    name: '연합 CTF',
    description: '다른 대학 보안 동아리와 함께 교류전을 엽니다.',
  },
]

// 공용 연락처, 정해지면 채운다
export const CONTACTS: Contact[] = []
