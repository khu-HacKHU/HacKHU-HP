#!/usr/bin/env bash
# 열린 이슈 전체에 레이블 규칙을 적용한다 (CONTRIBUTING.md 5절). 규칙을 바꿨거나 기존 이슈에 처음 적용할 때 PL이 로컬에서 돌린다.
#
#   DRY_RUN=1 bash .github/scripts/backfill-issue-labels.sh   # 미리보기: 바꿀 내용만 출력
#   bash .github/scripts/backfill-issue-labels.sh             # 적용
#
# gh에 로그인돼 있어야 한다. 안내 댓글은 달지 않고 문제만 출력한다.
# 워크플로의 workflow_dispatch로 두지 않은 이유: 미리보기 입력(inputs)이 Checkov CKV_GHA_7에 걸리고,
# 한 번 쓰는 작업이라 워크플로로 둘 이유가 적다.

set -euo pipefail

export GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-khu-HacKHU/HacKHU-HP}"
dir="$(dirname "${BASH_SOURCE[0]}")"

numbers="$(gh api "repos/$GITHUB_REPOSITORY/issues?state=open&per_page=100" --paginate \
  --jq '.[] | select(.pull_request | not) | .number')"
echo "대상 이슈 $(wc -w <<<"$numbers")개${DRY_RUN:+ (미리보기)}"

failed=0
for n in $numbers; do
  title="$(gh api "repos/$GITHUB_REPOSITORY/issues/$n" --jq .title </dev/null)"
  body="$(gh api "repos/$GITHUB_REPOSITORY/issues/$n" --jq '.body // ""' </dev/null)"
  if ! ISSUE_TITLE="$title" ISSUE_BODY="$body" bash "$dir/issue-labels.sh" "$n" --no-comment </dev/null; then
    failed=$((failed + 1))
  fi
done
echo "문제가 남은 이슈: ${failed}개"
