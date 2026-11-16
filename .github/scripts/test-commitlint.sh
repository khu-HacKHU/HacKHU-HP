#!/usr/bin/env bash
# commitlint 규칙이 실제로 거르는지 본다.
# 규칙은 켜 놓고 확인하지 않으면 조용히 죽는다 — 오타 하나로 type-enum이 비어도 모든 메시지가
# 통과한다. 그래서 통과해야 하는 예와 막혀야 하는 예를 함께 먹인다.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

failures=0

lint() {
  printf '%s' "$1" | npx --no -- commitlint >/dev/null 2>&1
}

expect_pass() {
  if ! lint "$1"; then
    echo "통과해야 하는 메시지가 막혔습니다: $1" >&2
    failures=$((failures + 1))
  fi
}

expect_fail() {
  if lint "$1"; then
    echo "막혀야 하는 메시지가 통과했습니다: $1" >&2
    failures=$((failures + 1))
  fi
}

# --- 통과 -------------------------------------------------------------------
expect_pass 'feat: 공지 목록 조회 API 구현'
expect_pass 'fix: 로그인 후 무한 리다이렉트 수정'
expect_pass 'security: 게시글 수정 API에 작성자 검사 추가'
expect_pass 'release: v0.1.0 출시'
expect_pass 'chore: bump dependencies'
expect_pass 'feat(board): 공지 목록 조회 API 구현'
expect_pass 'fix(auth): 토큰 갱신 실패 수정'
expect_pass 'feat(gallery): 사진 업로드 추가'
expect_pass 'chore(infra): VPC 모듈 추가'

for type in feat fix docs design cicd refactor test chore security release; do
  expect_pass "$type: 형식 확인"
done

# --- 거절 -------------------------------------------------------------------
expect_fail 'wip: 대충 저장'
expect_fail '공지 목록 구현'
expect_fail 'feat:'
expect_fail 'feat(web): 공지 화면 추가'
expect_fail 'feat(photo): 사진 업로드 추가'
expect_fail 'feat: 공지 조회 API 구현 #110'
expect_fail 'fix: 세션 갱신 수정 (#91)'
expect_fail "docs: $(printf '가%.0s' $(seq 1 80))"

# --- Dependabot 제목 길이 예외 ------------------------------------------------
# 길이 규칙은 COMMITLINT_SKIP_HEADER_LENGTH=1일 때만 열린다. 켜도 다른 규칙은 그대로 막아야 한다.
long_title='chore: bump org.springdoc:springdoc-openapi-starter-webmvc-ui from 3.1.1 to 3.2.0 in /backend in the backend-minor group'

# 평소에는 막힌다 (#95가 실제로 이걸로 막혔다).
expect_fail "$long_title"

# 예외를 켜면 통과한다.
if ! COMMITLINT_SKIP_HEADER_LENGTH=1 lint "$long_title"; then
  echo "Dependabot 예외를 켰는데 긴 제목이 막혔습니다" >&2
  failures=$((failures + 1))
fi

# 예외를 켜도 길이 외의 규칙은 느슨해지지 않는다.
for bad in 'wip: 대충 저장' 'feat(web): 공지 화면 추가' 'feat: 공지 조회 API 구현 #110'; do
  if COMMITLINT_SKIP_HEADER_LENGTH=1 lint "$bad"; then
    echo "Dependabot 예외가 길이 외의 규칙까지 열었습니다: $bad" >&2
    failures=$((failures + 1))
  fi
done

if [ "$failures" -ne 0 ]; then
  echo "commitlint 규칙 검사 실패: $failures건" >&2
  exit 1
fi
echo "commitlint 규칙 검사 통과"
