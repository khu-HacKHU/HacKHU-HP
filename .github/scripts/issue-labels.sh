#!/usr/bin/env bash
# 이슈 하나의 레이블을 맞추고 규칙을 검사한다 (CONTRIBUTING.md 5절). `Issue Labels` 워크플로가 쓴다.
#
#   ISSUE_TITLE=... ISSUE_BODY=... issue-labels.sh <이슈 번호> [--no-comment]
#
# - 제목의 type으로 type 레이블을, 제목 scope와 본문의 영역 체크박스로 영역 레이블을 붙인다.
# - 기능·버그 이슈(feature, bug)에 영역 레이블(backend, frontend, infra)이 하나도 없으면 문제로 본다.
# - 문제가 있으면 이슈에 안내 댓글을 하나 달고(이미 있으면 고친다) 실패한다. 문제가 없어지면 댓글을 지운다.
#   --no-comment면 댓글 없이 문제만 출력한다 (backfill-issue-labels.sh가 쓴다).
#
# GH_TOKEN, GITHUB_REPOSITORY가 필요하다. 제목과 본문은 사용자 입력이라 인자가 아닌 환경변수로 받는다.

set -euo pipefail

number="${1:?이슈 번호가 필요합니다}"
mode="${2:-}"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY가 필요합니다}"
title="${ISSUE_TITLE:-}"
body="${ISSUE_BODY:-}"
dir="$(dirname "${BASH_SOURCE[0]}")"
marker='<!-- issue-labels -->'
# 안내 댓글을 다는 계정. 로컬에서 시험할 때만 바꾼다.
bot="${BOT_LOGIN:-github-actions[bot]}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

problems=()

if printf '%s' "$body" | bash "$dir/labels.sh" checked-areas |
  bash "$dir/labels.sh" expected "$title" >"$work/expected" 2>/dev/null; then
  bash "$dir/apply-labels.sh" "$number" "$work/expected"

  if grep -qxE 'feature|bug' "$work/expected"; then
    if [ "${DRY_RUN:-}" = "1" ]; then
      cp "$work/expected" "$work/current" # 미리보기에서는 붙일 예정인 레이블로 판단한다
      gh api "repos/$repo/issues/$number/labels" --jq '.[].name' </dev/null >>"$work/current"
    else
      gh api "repos/$repo/issues/$number/labels" --jq '.[].name' </dev/null >"$work/current"
    fi
    if ! grep -qxE 'backend|frontend|infra' "$work/current"; then
      problems+=("기능·버그 이슈는 영역이 필요해요. 본문의 영역 체크박스(\`- [x] backend\`, \`- [x] frontend\`)에 체크하거나 \`backend\`, \`frontend\` 레이블을 직접 붙여 주세요. 인프라 작업이면 제목의 scope를 \`infra\`로 써요.")
    fi
  fi
else
  problems+=("제목이 \`type(scope): 한글 설명\` 형식이 아니에요. 예: \`feat(board): 공지 목록 조회\`. type 목록은 CONTRIBUTING.md 1절에 있어요.")
fi

if [ "$mode" = "--no-comment" ]; then
  for p in "${problems[@]}"; do
    echo "#$number 문제: $p"
  done
  [ ${#problems[@]} -eq 0 ]
  exit
fi

existing="$(gh api "repos/$repo/issues/$number/comments" --paginate \
  --jq ".[] | select(.user.login == \"$bot\" and (.body | contains(\"$marker\"))) | .id" </dev/null)"
existing="${existing%%$'\n'*}" # 여러 개면 첫 댓글만 쓴다

if [ ${#problems[@]} -eq 0 ]; then
  if [ -n "$existing" ]; then
    gh api -X DELETE "repos/$repo/issues/comments/$existing" </dev/null >/dev/null
    echo "#$number 안내 댓글을 지웠습니다."
  fi
  exit 0
fi

{
  echo "$marker"
  echo "이 이슈의 레이블을 자동으로 맞추지 못했어요. 아래를 고치면 레이블이 붙고 이 댓글은 지워져요 (CONTRIBUTING.md 5절)."
  echo
  for p in "${problems[@]}"; do
    echo "- $p"
  done
} >"$work/comment"

if [ -n "$existing" ]; then
  gh api -X PATCH "repos/$repo/issues/comments/$existing" -F "body=@$work/comment" </dev/null >/dev/null
else
  gh api -X POST "repos/$repo/issues/$number/comments" -F "body=@$work/comment" </dev/null >/dev/null
fi
for p in "${problems[@]}"; do
  echo "::error::#$number $p"
done
exit 1
