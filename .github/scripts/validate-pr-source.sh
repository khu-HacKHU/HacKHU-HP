#!/usr/bin/env bash
# PR 출발 브랜치 이름을 검사한다 (CONTRIBUTING.md 2절).
#   main 대상: release/vX.Y.Z, hotfix/vX.Y.Z만
#   develop, release/* 대상: 작업 브랜치 {type}/{이슈번호}-{slug}, 또는 release, hotfix, dependabot 브랜치

set -euo pipefail

base_ref="${1:?base branch is required}"
head_ref="${2:?head branch is required}"
release_pattern='^(release|hotfix)/v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
# type은 commitlint.config.mjs의 목록에서 release를 뺀 것이다. 둘을 같은 PR에서 함께 고친다.
work_pattern='^(feat|fix|security|docs|design|cicd|refactor|test|chore)/[1-9][0-9]*-[a-z0-9]+(-[a-z0-9]+)*$'

if [[ "$base_ref" == "main" ]]; then
  if [[ ! "$head_ref" =~ $release_pattern ]]; then
    echo "main 대상 PR은 release/vX.Y.Z, hotfix/vX.Y.Z 브랜치에서만 시작할 수 있습니다: $head_ref" >&2
    exit 1
  fi
  exit 0
fi

if [[ "$base_ref" == "develop" || "$base_ref" == release/* ]]; then
  if [[ "$head_ref" =~ $work_pattern || "$head_ref" =~ $release_pattern || "$head_ref" == dependabot/* ]]; then
    exit 0
  fi
  echo "작업 브랜치 이름은 {type}/{이슈번호}-{영어 kebab-case slug} 형식이어야 합니다 (예: feat/12-notice-list): $head_ref" >&2
  exit 1
fi
