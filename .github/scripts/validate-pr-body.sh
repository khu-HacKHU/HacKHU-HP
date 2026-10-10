#!/usr/bin/env bash
# PR 본문에 이슈를 닫는 키워드와 번호가 있는지 검사한다 (CONTRIBUTING.md 3절).
# 본문은 셸 해석을 피하려고 인자 대신 환경변수 PR_BODY로 받는다.
# dependabot과 release 브랜치는 이슈 없이 만들어지므로 검사하지 않는다.

set -euo pipefail

head_ref="${1:?head branch is required}"
body="${PR_BODY:-}"

if [[ "$head_ref" == dependabot/* || "$head_ref" == release/* ]]; then
  exit 0
fi

if ! grep -Eiq '(^|[^a-z])(close[sd]?|fix(e[sd])?|resolve[sd]?):? #[0-9]+' <<<"$body"; then
  echo "PR 본문에 연결할 이슈를 적어 주세요 (예: Closes #12)." >&2
  exit 1
fi
