#!/usr/bin/env bash
# validate-pr-body.sh 규칙 자체를 검사한다.

set -euo pipefail

script="$(dirname "${BASH_SOURCE[0]}")/validate-pr-body.sh"
failures=0

# run <head_ref> <body> [author] [from_fork]
# 작성자 기본값은 사람이고 포크 기본값은 아니다. 예외는 그 둘로만 열린다.
run() {
  PR_BODY="$2" bash "$script" "$1" "${3:-somebody}" "${4:-false}" >/dev/null 2>&1
}

expect_pass() {
  if ! run "$@"; then
    echo "통과해야 하는 본문이 막혔습니다 ($1, 작성자=${3:-somebody}, 포크=${4:-false}): $2" >&2
    failures=$((failures + 1))
  fi
}

expect_fail() {
  if run "$@"; then
    echo "막혀야 하는 본문이 통과했습니다 ($1, 작성자=${3:-somebody}, 포크=${4:-false}): $2" >&2
    failures=$((failures + 1))
  fi
}

expect_pass feat/12-notice-list 'Closes #12'
expect_pass feat/12-notice-list $'## 변경 이유\n\n공지 목록\n\nCloses #12'
expect_pass fix/31-login-loop 'fixes #31'
expect_pass fix/31-login-loop 'Resolves: #31'
expect_pass hotfix/v0.1.1 'Closes #50'
expect_pass dependabot/npm_and_yarn/develop/tooling-1172afec0c 'Bumps the tooling group' 'dependabot[bot]'
expect_pass release/v0.1.0 '출시 PR'
# 작성자가 Dependabot이면 브랜치 이름과 무관하게 통과한다.
expect_pass feat/12-notice-list '이슈 없음' 'dependabot[bot]'

expect_fail feat/12-notice-list ''
expect_fail feat/12-notice-list 'Closes #'
expect_fail feat/12-notice-list '#12 관련 작업'
expect_fail feat/12-notice-list 'Refs #12'
expect_fail feat/12-notice-list 'encloses #12'
expect_fail hotfix/v0.1.1 '긴급 수정'

# --- 브랜치 이름 위장 (#101 리뷰) ----------------------------------------------
# 예외는 작성자와 포크 여부로만 열린다. 이름만 흉내 내도 막혀야 한다.
expect_fail dependabot/npm_and_yarn/develop/tooling-1172afec0c '이슈 없음'
expect_fail dependabot/fake '이슈 없음' 'attacker'
expect_fail release/v9.9.9 '이슈 없음' 'attacker' true
expect_fail dependabot/fake '이슈 없음' 'dependabot' 
expect_fail dependabot/fake '이슈 없음' 'dependabot[bot]-not' 

if [ "$failures" -ne 0 ]; then
  echo "PR 본문 규칙 검사 실패: $failures건" >&2
  exit 1
fi
echo "PR 본문 규칙 검사 통과"
