#!/usr/bin/env bash
# validate-pr-body.sh 규칙 자체를 검사한다.

set -euo pipefail

script="$(dirname "${BASH_SOURCE[0]}")/validate-pr-body.sh"
failures=0

expect_pass() {
  if ! PR_BODY="$2" bash "$script" "$1" >/dev/null 2>&1; then
    echo "통과해야 하는 본문이 막혔습니다 ($1): $2" >&2
    failures=$((failures + 1))
  fi
}

expect_fail() {
  if PR_BODY="$2" bash "$script" "$1" >/dev/null 2>&1; then
    echo "막혀야 하는 본문이 통과했습니다 ($1): $2" >&2
    failures=$((failures + 1))
  fi
}

expect_pass feat/12-notice-list 'Closes #12'
expect_pass feat/12-notice-list $'## 변경 이유\n\n공지 목록\n\nCloses #12'
expect_pass fix/31-login-loop 'fixes #31'
expect_pass fix/31-login-loop 'Resolves: #31'
expect_pass hotfix/v0.1.1 'Closes #50'
expect_pass dependabot/npm_and_yarn/develop/tooling-1172afec0c 'Bumps the tooling group'
expect_pass release/v0.1.0 '출시 PR'

expect_fail feat/12-notice-list ''
expect_fail feat/12-notice-list 'Closes #'
expect_fail feat/12-notice-list '#12 관련 작업'
expect_fail feat/12-notice-list 'Refs #12'
expect_fail feat/12-notice-list 'encloses #12'
expect_fail hotfix/v0.1.1 '긴급 수정'

if [ "$failures" -ne 0 ]; then
  echo "PR 본문 규칙 검사 실패: $failures건" >&2
  exit 1
fi
echo "PR 본문 규칙 검사 통과"
