#!/usr/bin/env bash
# PR·이슈 레이블 규칙 (CONTRIBUTING.md 5절). `PR Labels`, `Issue Labels` 워크플로가 쓴다.
#
#   labels.sh expected "<제목>" < 경로목록
#       붙어야 할 레이블을 한 줄에 하나씩 출력한다. type 레이블 1개 + 해당하는 영역 레이블.
#       PR은 변경 파일 목록을, 이슈는 `checked-areas`의 출력을 넣는다.
#   labels.sh checked-areas < 이슈본문
#       이슈 본문의 영역 체크박스(`- [x] backend`)에서 체크된 영역을 `backend/`처럼 경로로 출력한다.
#   labels.sh diff <붙어야 할 레이블 파일> <현재 레이블 파일>
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
TYPE_LABELS=(feature bug documentation security design cicd refactor test chore release)

# 영역 레이블. 경로(또는 이슈 체크박스)로 붙이기만 하고 떼지 않는다 — 사람이 직접 붙여도 된다.
AREAS=(backend frontend infra)

expected() {
  local title="$1"
  # type, 선택적 (scope), 콜론 뒤 공백과 설명
  local pattern='^([a-z]+)(\(([a-z]+)\))?: .+'
  if [[ ! "$title" =~ $pattern ]]; then
    echo "제목이 'type: 설명' 또는 'type(scope): 설명' 형식이 아닙니다: $title" >&2
    return 1
  fi
  local type="${BASH_REMATCH[1]}" scope="${BASH_REMATCH[3]}" label
  if ! label="$(type_label "$type")"; then
    echo "레이블 규칙에 없는 type입니다: $type (CONTRIBUTING.md 1절)" >&2
    return 1
  fi
  echo "$label"

  local -A hit=()
  local file area
  if [[ "$scope" == "infra" ]]; then
    hit[infra]=1
  fi
  while IFS= read -r file || [[ -n "$file" ]]; do
    for area in "${AREAS[@]}"; do
      if [[ "$file" == "$area"/* ]]; then
        hit[$area]=1
      fi
    done
  done
  for area in "${AREAS[@]}"; do
    if [[ -n "${hit[$area]:-}" ]]; then
      echo "$area"
    fi
  done
}

checked_areas() {
  local line area
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    for area in "${AREAS[@]}"; do
      if [[ "$line" =~ ^[[:space:]]*[-*][[:space:]]+\[[xX]\][[:space:]]+${area}([[:space:]]|$) ]]; then
        echo "$area/"
      fi
    done
  done
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
  expected) expected "${2:?제목이 필요합니다}" ;;
  checked-areas) checked_areas ;;
  diff) diff_labels "${2:?붙어야 할 레이블 파일이 필요합니다}" "${3:?현재 레이블 파일이 필요합니다}" ;;
  *)
    echo "사용법: $0 expected <제목> | checked-areas | diff <붙어야 할 레이블 파일> <현재 레이블 파일>" >&2
    exit 2
    ;;
esac
