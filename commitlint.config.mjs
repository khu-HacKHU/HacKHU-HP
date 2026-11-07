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
  // 출시 PR(`release/vX.Y.Z`, `hotfix/vX.Y.Z → main`) 전용. 목록에서 출시를 잡일과 구분하기 위해 둔다.
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

/**
 * Dependabot PR은 제목 길이 검사를 끈다.
 *
 * 제목이 Dependabot이 만드는 것이라 우리가 줄일 수 없다. 긴 Gradle 좌표가 단독으로 그룹
 * 업데이트될 때 `chore: bump org.springdoc:springdoc-openapi-starter-webmvc-ui from 3.1.1 to
 * 3.2.0 in /backend in the backend-minor group`처럼 120자가 되어 막혔다 (#95).
 *
 * 길이 규칙만 끈다. type-enum, scope-enum, 이슈 번호 금지는 그대로 적용된다.
 * 이슈 연결 검사가 이미 dependabot 브랜치를 예외로 두고 있어(CONTRIBUTING.md 3절) 같은 선례다.
 * 판단은 `Lint PR` 워크플로가 head 브랜치 이름으로 하고 여기에 이 환경변수로 넘긴다.
 */
const isDependabot = process.env.COMMITLINT_SKIP_HEADER_LENGTH === '1'

export default {
  extends: ['@commitlint/config-conventional'],
  plugins: [noIssueNumber],
  rules: {
    'type-enum': [2, 'always', TYPES],
    // scope는 팀 영역이고 선택이다. 모노레포라 어느 팀의 변경인지 제목에서 바로 보이게 한다.
    'scope-enum': [2, 'always', ['auth', 'board', 'gallery', 'infra']],
    // 한글에는 대소문자가 없어 subject-case가 의미가 없다.
    'subject-case': [0],
    // squash merge가 " (#123)"을 덧붙이므로 기본값 100보다 낮춘다.
    'header-max-length': isDependabot ? [0] : [2, 'always', 72],
    'subject-no-issue-number': [2, 'always'],
  },
}
