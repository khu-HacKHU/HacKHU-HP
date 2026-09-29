/**
 * 커밋 메시지 규칙. 원본은 CONTRIBUTING.md 1절이고 여기는 그것을 기계가 읽는 형태로 옮긴 것이다.
 * 문서와 다른 말을 하면 안 된다 — 둘을 같은 PR에서 함께 고친다.
 *
 * 관문은 CI다. 로컬 훅은 편의라서 설치하지 않은 사람도 막히지 않는다.
 * squash merge에서 히스토리에 남는 것은 PR 제목이므로 `Lint PR` 워크플로가 그것을 검사한다.
 */

/** CONTRIBUTING.md 1절의 표. */
const TYPES = [
  'feat',
  'fix',
  'docs',
  'design',
  'cicd',
  'refactor',
  'test',
  'chore',
  'security',
  // 출시 PR(`release/vX.Y.Z → main`) 전용. 목록에서 출시를 잡일과 구분하기 위해 둔다.
  'release',
]

/** 제목에 이슈 번호를 넣지 않는다. squash merge가 `(#123)`을 자동으로 붙인다. */
const noIssueNumber = {
  rules: {
    'subject-no-issue-number': ({ subject }) => [
      !/#\d+/.test(subject ?? ''),
      '제목에 이슈 번호를 넣지 않는다. 연결은 브랜치명과 PR 본문이 한다 (CONTRIBUTING.md 1절)',
    ],
  },
}

export default {
  extends: ['@commitlint/config-conventional'],
  plugins: [noIssueNumber],
  rules: {
    'type-enum': [2, 'always', TYPES],
    // scope는 쓰지 않는다. 어느 영역인지는 diff가 보여준다.
    'scope-empty': [2, 'always'],
    // 한글에는 대소문자가 없어 subject-case가 의미가 없다.
    'subject-case': [0],
    // squash merge가 " (#123)"을 덧붙이므로 기본값 100보다 낮춘다.
    'header-max-length': [2, 'always', 72],
    'subject-no-issue-number': [2, 'always'],
  },
}
