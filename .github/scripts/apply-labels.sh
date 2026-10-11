#!/usr/bin/env bash
# PR이나 이슈의 레이블을 붙어야 할 레이블에 맞추고, 맞았는지 다시 확인한다 (CONTRIBUTING.md 5절).
#
#   apply-labels.sh <PR·이슈 번호> <붙어야 할 레이블 파일>
#
# GH_TOKEN, GITHUB_REPOSITORY 환경변수가 필요하다. 붙이거나 떼지 못하면(읽기 전용 토큰 등)
# 경고만 남기고, 마지막 확인에서 어긋나 있으면 실패한다.
# 반복문이 읽는 입력을 gh가 가로채지 않도록 gh의 stdin은 /dev/null로 막는다.

set -euo pipefail

number="${1:?PR·이슈 번호가 필요합니다}"
expected_file="${2:?붙어야 할 레이블 파일이 필요합니다}"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY가 필요합니다}"
labels_sh="$(dirname "${BASH_SOURCE[0]}")/labels.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

current_diff() {
  gh api "repos/$repo/issues/$number/labels" --jq '.[].name' >"$work/current"
  bash "$labels_sh" diff "$expected_file" "$work/current"
}

current_diff >"$work/changes"

# DRY_RUN=1이면 바꿀 내용만 보여 주고 끝낸다 (backfill-issue-labels.sh 미리보기).
if [ "${DRY_RUN:-}" = "1" ]; then
  if [ -s "$work/changes" ]; then
    echo "#$number 바꿀 예정: $(paste -sd' ' "$work/changes")"
  fi
  exit 0
fi

while IFS= read -r change; do
  label="${change:1}"
  if [ "${change:0:1}" = "+" ]; then
    echo "#$number 붙임: $label"
    gh api -X POST "repos/$repo/issues/$number/labels" -f "labels[]=$label" </dev/null >/dev/null ||
      echo "::warning::#$number에 '$label' 레이블을 붙이지 못했습니다."
  else
    echo "#$number 뗌: $label"
    gh api -X DELETE "repos/$repo/issues/$number/labels/$label" </dev/null >/dev/null ||
      echo "::warning::#$number에서 '$label' 레이블을 떼지 못했습니다."
  fi
done <"$work/changes"

current_diff >"$work/changes"
if [ -s "$work/changes" ]; then
  echo "::error::#$number 레이블이 제목과 맞지 않습니다. 붙일 것(+)·뗄 것(-): $(paste -sd' ' "$work/changes") (CONTRIBUTING.md 5절)"
  exit 1
fi
echo "#$number 레이블 확인 완료: $(paste -sd, "$work/current")"
