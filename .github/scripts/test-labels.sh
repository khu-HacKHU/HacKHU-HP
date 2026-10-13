#!/usr/bin/env bash
# labels.sh 규칙 자체를 검사한다.
# type 하나가 목록에서 빠지거나 매핑이 바뀌면 그 type의 PR이 전부 막히므로 type마다 확인한다.

set -euo pipefail

script="$(dirname "${BASH_SOURCE[0]}")/labels.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
failures=0

# expect_labels <제목> <변경 파일(줄바꿈 구분)> <기대 출력(줄바꿈 구분)>
expect_labels() {
  local actual
  if ! actual="$(printf '%s' "$2" | bash "$script" expected "$1" 2>/dev/null)"; then
    echo "레이블 계산이 실패했습니다: $1" >&2
    failures=$((failures + 1))
    return
  fi
  if [[ "$actual" != "$3" ]]; then
    echo "레이블이 다릅니다: $1" >&2
    echo "  기대: $(echo "$3" | paste -sd, -)  실제: $(echo "$actual" | paste -sd, -)" >&2
    failures=$((failures + 1))
  fi
}

expect_reject() {
  if printf '' | bash "$script" expected "$1" >/dev/null 2>&1; then
    echo "막혀야 하는 제목이 통과했습니다: $1" >&2
    failures=$((failures + 1))
  fi
}

# expect_diff <붙어야 할 레이블> <현재 레이블> <기대 출력>
expect_diff() {
  printf '%s' "$1" >"$tmp/expected"
  printf '%s' "$2" >"$tmp/current"
  local actual
  actual="$(bash "$script" diff "$tmp/expected" "$tmp/current")"
  if [[ "$actual" != "$3" ]]; then
    echo "diff가 다릅니다: 기대=[$1] 현재=[$2]" >&2
    echo "  기대 출력: $(echo "$3" | paste -sd, -)  실제: $(echo "$actual" | paste -sd, -)" >&2
    failures=$((failures + 1))
  fi
}

# --- type → 레이블 ----------------------------------------------------------
expect_labels 'feat: 공지 목록 조회 API 구현' '' 'feature'
expect_labels 'fix: 로그인 후 무한 리다이렉트 수정' '' 'bug'
expect_labels 'docs: API 규약 갱신' '' 'documentation'
expect_labels 'security: 게시글 수정 API에 작성자 검사 추가' '' 'security'
for type in design cicd refactor test chore release; do
  expect_labels "$type: 형식 확인" '' "$type"
done
expect_labels 'release: v0.1.0 출시' '' 'release'

# scope가 있어도 type으로 정한다
expect_labels 'feat(auth): 로그인 API 구현' '' 'feature'
expect_labels 'test(board): 권한 거부 테스트' '' 'test'

# --- 영역 (scope, 경로) ------------------------------------------------------
expect_labels 'cicd(infra): 스테이징 배포' '' $'cicd\ninfra'
expect_labels 'feat(gallery): presigned URL 발급' $'backend/a.java\ninfra/s3.tf' $'feature\nbackend\ninfra'
expect_labels 'chore: 버킷 이름 변경' 'infra/main.tf' $'chore\ninfra'
expect_labels 'docs: 인프라 설명' $'docs/a.md\ninfrastructure.md' 'documentation'
expect_labels 'feat(board): 글 작성' $'backend/a.java\nfrontend/b.tsx' $'feature\nbackend\nfrontend'
expect_labels 'fix(auth): 로그인 화면 오류' 'frontend/src/Login.tsx' $'bug\nfrontend'
# 이름이 영역으로 시작하기만 하는 파일은 그 영역이 아니다
expect_labels 'chore: 설정' $'backend.md\nfrontend-notes.md' 'chore'

# --- 이슈 제목 (변경 파일 없음) ---------------------------------------------
expect_labels 'security: [Semgrep] 게시글 검색 SQL 문자열 연결' '' 'security'
expect_labels 'feat(infra): 스테이징 환경 Terraform' '' $'feature\ninfra'
expect_labels 'docs(board): ERD 작성' '' 'documentation'

# --- 이슈 본문 체크박스 → 영역 ---------------------------------------------
# expect_areas <본문> <기대 출력>
expect_areas() {
  local actual
  actual="$(printf '%s' "$1" | bash "$script" checked-areas)"
  if [[ "$actual" != "$2" ]]; then
    echo "체크된 영역이 다릅니다: [$1]" >&2
    echo "  기대: $(echo "$2" | paste -sd, -)  실제: $(echo "$actual" | paste -sd, -)" >&2
    failures=$((failures + 1))
  fi
}
expect_areas $'## 영역\n\n- [x] backend\n- [ ] frontend' 'backend/'
expect_areas $'- [X] backend\n* [x] frontend' $'backend/\nfrontend/'
expect_areas $'  - [x] frontend\r\n' 'frontend/'
expect_areas $'- [ ] backend\n- [ ] frontend' ''
expect_areas $'backend 작업\n- [x] backendless\n- [x]backend' ''

# 체크박스에서 레이블까지 이어서
chained="$(printf '%s' $'- [x] backend\n- [x] frontend' | bash "$script" checked-areas | bash "$script" expected 'feat(auth): 로그인')"
if [[ "$chained" != $'feature\nbackend\nfrontend' ]]; then
  echo "체크박스에서 레이블까지 이어진 결과가 다릅니다: $(echo "$chained" | paste -sd, -)" >&2
  failures=$((failures + 1))
fi

# --- 거절 -------------------------------------------------------------------
expect_reject 'wip: 대충 저장'
expect_reject '공지 목록 구현'
expect_reject 'feat:'
expect_reject 'feat:설명 앞 공백 없음'
expect_reject 'Feat: 대문자 type'

# --- diff -------------------------------------------------------------------
expect_diff 'feature' 'feature' ''
expect_diff 'feature' '' '+feature'
expect_diff $'cicd\ninfra' 'cicd' '+infra'
# 잘못 붙은 type 레이블은 뗀다
expect_diff 'bug' $'feature\nbug' '-feature'
expect_diff 'chore' 'feature' $'+chore\n-feature'
# type 레이블이 아닌 것(dependencies, infra 등)은 건드리지 않는다
expect_diff 'chore' $'chore\ndependencies\njava' ''
expect_diff 'feature' $'feature\ninfra' ''
# 영역 레이블은 떼지 않는다
expect_diff $'bug\nfrontend' $'bug\nbackend\nfrontend' ''

if [ "$failures" -ne 0 ]; then
  echo "레이블 규칙 검사 실패: $failures건" >&2
  exit 1
fi
echo "레이블 규칙 검사 통과"
