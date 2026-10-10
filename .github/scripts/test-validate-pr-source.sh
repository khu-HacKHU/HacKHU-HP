#!/usr/bin/env bash
# validate-pr-source.sh 규칙 자체를 검사한다.

set -euo pipefail

script="$(dirname "${BASH_SOURCE[0]}")/validate-pr-source.sh"
failures=0

expect_pass() {
  if ! bash "$script" "$1" "$2" >/dev/null 2>&1; then
    echo "통과해야 하는 조합이 막혔습니다: $2 -> $1" >&2
    failures=$((failures + 1))
  fi
}

expect_fail() {
  if bash "$script" "$1" "$2" >/dev/null 2>&1; then
    echo "막혀야 하는 조합이 통과했습니다: $2 -> $1" >&2
    failures=$((failures + 1))
  fi
}

expect_pass main release/v0.1.0
expect_pass main release/v1.12.3
expect_pass develop feat/12-notice-list
expect_pass develop fix/31-login-loop
expect_pass release/v0.1.0 fix/42-release-blocker
expect_pass main hotfix/v0.1.1
expect_pass develop hotfix/v0.1.1
expect_pass develop release/v0.1.0
expect_pass develop chore/7-frontend-react-setup
expect_pass develop docs/13-feature-spec
expect_pass develop security/40-post-owner-check
expect_pass develop dependabot/npm_and_yarn/develop/tooling-1172afec0c
expect_pass feat/12-notice-list feat/12-notice-list-ui

expect_fail main develop
expect_fail main feat/12-notice-list
expect_fail main release/0.1.0
expect_fail main release/v0.1
expect_fail main release/v01.0.0
expect_fail main hotfix/0.1.1
expect_fail main hotfix/31-login-loop
expect_fail develop chore/backend-spring-setup
expect_fail develop docs/spec-draft
expect_fail develop feat/notice-list
expect_fail develop feat/12_notice_list
expect_fail develop feat/12-Notice-List
expect_fail develop feat/012-notice-list
expect_fail develop wip/12-notice-list
expect_fail develop release/12-notice-list
expect_fail release/v0.1.0 bugfix/42-release-blocker

if [ "$failures" -ne 0 ]; then
  echo "PR 출발 브랜치 규칙 검사 실패: $failures건" >&2
  exit 1
fi
echo "PR 출발 브랜치 규칙 검사 통과"
