#!/usr/bin/env bash
# PR 레이블 규칙 (CONTRIBUTING.md 5절). `PR Labels` 워크플로가 쓴다.
#
#   pr-labels.sh expected "<PR 제목>" < 변경파일목록
#       붙어야 할 레이블을 한 줄에 하나씩 출력한다. type 레이블 1개 + 해당하면 infra.
#   pr-labels.sh diff <붙어야 할 레이블 파일> <현재 레이블 파일>
#       맞추려면 붙일 레이블은 `+이름`, 뗄 레이블은 `-이름`으로 출력한다. 맞으면 아무것도 출력하지 않는다.
#
# type 목록은 commitlint.config.mjs·CONTRIBUTING.md 1절과 같아야 한다.

set -euo pipefail

# type → 레이블. docs·feat·fix는 기존 레이블 이름을 그대로 쓴다.
type_label() {
  case "$1" in
    feat) echo feature ;;
    fix) echo bug ;;
    docs) echo documentation ;;
    security | design | cicd | refactor | test | chore | release) echo "$1" ;;
    *) return 1 ;;
  esac
}

# 자동으로 관리하는 type 레이블. 이 중 붙어야 할 것 외에는 뗀다.
# infra는 type 레이블이 아니라서 자동으로 붙이기만 하고 떼지 않는다 — 사람이 직접 붙여도 된다.
TYPE_LABELS=(feature bug documentation security design cicd refactor test chore release)

expected() {
  local title="$1"
  # type, 선택적 (scope), 콜론 뒤 공백과 설명
  local pattern='^([a-z]+)(\(([a-z]+)\))?: .+'
  if [[ ! "$title" =~ $pattern ]]; then
    echo "PR 제목이 'type: 설명' 또는 'type(scope): 설명' 형식이 아닙니다: $title" >&2
    return 1
  fi
  local type="${BASH_REMATCH[1]}" scope="${BASH_REMATCH[3]}" label
  if ! label="$(type_label "$type")"; then
    echo "레이블 규칙에 없는 type입니다: $type (CONTRIBUTING.md 1절)" >&2
    return 1
  fi
  echo "$label"

  local infra=false file
  if [[ "$scope" == "infra" ]]; then
    infra=true
  fi
  while IFS= read -r file || [[ -n "$file" ]]; do
    if [[ "$file" == infra/* ]]; then
      infra=true
    fi
  done
  if [[ "$infra" == true ]]; then
    echo infra
  fi
}

diff_labels() {
  local expected_file="$1" current_file="$2" label
  # 마지막 줄에 줄바꿈이 없어도 읽는다
  while IFS= read -r label || [[ -n "$label" ]]; do
    [[ -z "$label" ]] && continue
    if ! grep -qxF -- "$label" "$current_file"; then
      echo "+$label"
    fi
  done <"$expected_file"
  for label in "${TYPE_LABELS[@]}"; do
    if grep -qxF -- "$label" "$current_file" && ! grep -qxF -- "$label" "$expected_file"; then
      echo "-$label"
    fi
  done
}

case "${1:-}" in
  expected) expected "${2:?PR 제목이 필요합니다}" ;;
  diff) diff_labels "${2:?붙어야 할 레이블 파일이 필요합니다}" "${3:?현재 레이블 파일이 필요합니다}" ;;
  *)
    echo "사용법: $0 expected <제목> | diff <붙어야 할 레이블 파일> <현재 레이블 파일>" >&2
    exit 2
    ;;
esac
