# 보안 도구

## 라운드별 도입

| 영역 | 도구 | 열리는 시점 | 설정 위치 |
|---|---|---|---|
| SAST | Semgrep, CodeQL | 1R (S2) | [security.yml](../../.github/workflows/security.yml), [codeql.yml](../../.github/workflows/codeql.yml) |
| SCA | Dependabot | 1R (S2) | [dependabot.yml](../../.github/dependabot.yml), 저장소 설정 |
| 시크릿 | gitleaks, GitHub secret scanning | 1R (S2) | [security.yml](../../.github/workflows/security.yml), [.gitleaks.toml](../../.gitleaks.toml) |
| IaC | Checkov | 1R (S2) | [security.yml](../../.github/workflows/security.yml) |
| 컨테이너 | Trivy, ECR 스캔 | 2R (스테이징 배포 후) | 미설정 |
| DAST | OWASP ZAP | 2R (7주차) | 미설정 |
| 보안 회귀 테스트 | 테스트 코드 | 3R | 각 팀 테스트 |

## 1R 현재 설정

| 도구 | 언제 도는가 | PR을 막는가 | 결과 보는 곳 |
|---|---|---|---|
| gitleaks | PR(추가된 커밋만), develop·main push, 매주 월요일 | **막는다** | Actions 로그 |
| Semgrep | PR, push, 매주 월요일 | 막지 않는다 | Security → Code scanning (`semgrep`) |
| CodeQL | PR, push, 매주 월요일 | 막지 않는다 | Security → Code scanning (`/language:*`) |
| Checkov | PR, push, 매주 월요일 | 막지 않는다 (`soft_fail`) | Security → Code scanning (`checkov`) |
| Dependabot | 주 1회 (Actions), 월 1회 (npm) + 취약점 발견 즉시 | 막지 않는다 | Security → Dependabot, PR 목록 |
| secret scanning | push 시 | push protection이 막는다 | Security → Secret scanning |

gitleaks만 막는 이유: 한 번 푸시된 시크릿은 되돌릴 수 없다. 나머지는 오탐이 섞여 있어서, **1R 담당자가 결과를 판별하고 차단 기준을 정하는 것이 1R의 목표다.**

## 1R 담당자가 할 일

| 영역 | 담당 | 할 일 |
|---|---|---|
| SAST | 양수환, 이나은 | Semgrep 규칙셋 조정 (`p/java` 적용됨, Spring 규칙 검토), CodeQL `java-kotlin` 결과와 비교, 경고 판별 |
| SCA·시크릿 | 박지은, 김유석 | Dependabot(`gradle` 적용됨) 알림 판별, 프론트 ecosystem 추가, gitleaks 오탐 관리 |
| IaC | 서영채, 이소연 | Checkov 경고 판별, `infra/` PR 리뷰 참여, 스킵 기준 문서화 |

판별 결과는 이슈로 남긴다: 제목 `security: [도구] 경고 요약`, `security` 라벨.

## 결과 판별

경고 하나마다 셋 중 하나로 분류하고 근거를 남긴다.

| 판정 | 처리 |
|---|---|
| **진짜 취약점** | 이슈 등록 → 담당 팀이 `security:` PR로 수정 → 재스캔으로 사라졌는지 확인 |
| **오탐** | 억제하고 근거를 PR 본문에 적는다 (아래) |
| **위험 수용** | 지금 고치지 않는 이유와 기한을 이슈에 적는다. Code scanning에서 "Won't fix"로 닫는다 |

### 억제 방법

한 줄 단위로 좁게 억제한다. 파일·규칙 전체를 끄는 것은 PL 승인이 필요하다.

```text
# Semgrep — 해당 줄 끝에
password = get_from_vault()  # nosemgrep: 규칙ID — 근거

# Checkov — 해당 리소스 블록 안에
# checkov:skip=CKV_AWS_18: 근거

# gitleaks — .gitleaks.toml allowlist에 경로·정규식 추가 (PR 본문에 근거)
```

## 시크릿이 유출됐을 때

1. **즉시 키를 폐기·재발급한다.** 커밋 삭제나 force push로는 해결되지 않는다 (포크·캐시·클론에 남는다).
2. PL에게 알린다.
3. 새 키는 GitHub Secrets에 넣는다.
4. 해당 커밋을 제거할지는 PL이 판단한다.

## 로컬에서 돌려 보기

Docker만 있으면 된다. 저장소 루트에서 실행한다.

```bash
# 시크릿
docker run --rm -v "$PWD:/repo" -w /repo ghcr.io/gitleaks/gitleaks:v8.30.1 git --redact --verbose .

# SAST
docker run --rm -v "$PWD:/src" -w /src semgrep/semgrep:1.178.0 semgrep scan --metrics=off --config p/default

# IaC
docker run --rm -v "$PWD:/tf" -w /tf bridgecrew/checkov -d infra
```

Juice Shop을 대상으로 한 실습은 [practice/juice-shop](../../practice/juice-shop/)에 있다.
