#!/usr/bin/env bash
# PR 본문에 이슈를 닫는 키워드와 번호가 있는지 검사한다 (CONTRIBUTING.md 3절).
# 본문은 셸 해석을 피하려고 인자 대신 환경변수 PR_BODY로 받는다.
# dependabot과 release PR은 이슈 없이 만들어지므로 검사하지 않는다.
#
# 예외 판별에 브랜치 이름만 쓰지 않는다. 포크 소유자가 자기 브랜치 이름을 정할 수 있어서,
# dependabot/이나 release/로 시작하는 이름으로 PR을 올리면 예외를 받아 갔다 (#101 리뷰).
# Dependabot은 작성자로, release는 우리 레포의 브랜치인지로 판별한다.

set -euo pipefail

head_ref="${1:?head branch is required}"
pr_author="${2:-}"
from_fork="${3:-false}"
body="${PR_BODY:-}"

if [[ "$pr_author" == 'dependabot[bot]' ]]; then
  exit 0
fi

if [[ "$head_ref" == release/* && "$from_fork" != true ]]; then
  exit 0
fi

if ! grep -Eiq '(^|[^a-z])(close[sd]?|fix(e[sd])?|resolve[sd]?):? #[0-9]+' <<<"$body"; then
  echo "PR 본문에 연결할 이슈를 적어 주세요 (예: Closes #12)." >&2
  exit 1
fi
