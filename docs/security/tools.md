# 보안 도구

## 스프린트별 도입

| 영역 | 도구 | 열리는 시점 | 설정 위치 |
|---|---|---|---|
| SAST | Semgrep, CodeQL | S3 | [security.yml](../../.github/workflows/security.yml), [codeql.yml](../../.github/workflows/codeql.yml) |
| SCA | Dependabot | S3 | [dependabot.yml](../../.github/dependabot.yml), 저장소 설정 |
| 시크릿 | gitleaks, GitHub secret scanning | S3 | [security.yml](../../.github/workflows/security.yml), [.gitleaks.toml](../../.gitleaks.toml) |
| IaC | Checkov | S3 | [security.yml](../../.github/workflows/security.yml) |
| 컨테이너 | Trivy, ECR 스캔 | S4 (스테이징 배포 후) | 미설정 |
| DAST | OWASP ZAP | S4 | 미설정 |
| 보안 회귀 테스트 | 테스트 코드 | S5 | 각 팀 테스트 |

## 현재 설정

| 도구 | 언제 도는가 | PR을 막는가 | 결과 보는 곳 |
|---|---|---|---|
| gitleaks | PR(추가된 커밋만), develop·main push, 매주 월요일 | **막는다** | Actions 로그 |
| Semgrep | PR, push, 매주 월요일 | 막지 않는다 | Security → Code scanning (`semgrep`) |
| CodeQL | PR, push, 매주 월요일 | 막지 않는다 | Security → Code scanning (`/language:*`) |
| Checkov | PR, push, 매주 월요일 | 막지 않는다 (`soft_fail`) | Security → Code scanning (`checkov`) |
| Dependabot | 주 1회 (Actions), 월 1회 (npm) + 취약점 발견 즉시 | 막지 않는다 | Security → Dependabot, PR 목록 |
| secret scanning | push 시 | push protection이 막는다 | Security → Secret scanning |

gitleaks만 막는 이유: 한 번 푸시된 시크릿은 되돌릴 수 없다. 나머지는 오탐이 섞여 있어서 막지 않고 PR 작성자가 판별한다. 결과가 쌓이면 PL이 차단 기준을 정한다.

## 누가 무엇을 하는가

도구별 담당자는 따로 두지 않는다. 도구는 CI에서 돌고, 각자 자기 PR에서 결과를 본다.

| 상황 | 하는 일 |
|---|---|
| 자기 PR에 보안 경고가 뜸 | 작성자가 아래 "결과 판별"대로 판정하고 근거를 PR에 코멘트로 남긴다 |
| Dependabot PR이나 알림을 봄 | 처음 본 사람이 Slack으로 PM과 PL에게 알린다 |
| 시크릿 유출을 발견함 | 아래 "시크릿이 유출됐을 때"를 따른다 |
| 새 도구를 붙일 때가 됨 | 지원자를 받아 이슈로 배정하고 PL이 리뷰한다 |

그 PR에서 고치지 않는 경고는 이슈로 남긴다: 제목 `security: [도구] 경고 요약`, `security` 라벨.

## 결과 판별

경고 하나마다 셋 중 하나로 분류하고 근거를 남긴다.

| 판정 | 처리 |
|---|---|
| **진짜 취약점** | 그 PR에서 고친다. 따로 고쳐야 하면 이슈를 등록하고 그 코드를 맡은 팀이 `security:` PR로 고친다. 재스캔으로 사라졌는지 확인한다 |
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
2. PM과 PL에게 바로 알린다.
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
