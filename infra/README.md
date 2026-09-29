# infra

AWS 인프라 Terraform. S2 3주차부터 갤러리 + 인프라 팀이 작성한다. 코드 오너는 PM·인프라팀이다 ([CODEOWNERS](../.github/CODEOWNERS)).

## 규칙

- AWS 변경은 **이 디렉터리의 PR로만** 한다. 콘솔에서 직접 바꾸지 않는다.
- GitHub Actions의 AWS 접근은 **OIDC**로만 한다. 액세스 키를 발급하지 않는다.
- IAM은 최소 권한. 예산 알림을 설정한다.
- `terraform.tfvars`, `*.tfstate`는 커밋하지 않는다 (`.gitignore`로 막혀 있다). 변수 예시는 `terraform.tfvars.example`에 값 없이 둔다.
- PR에서 CI가 `terraform fmt -check`와 `validate`를 돌리고, Checkov가 설정 취약점을 Code scanning에 보고한다.
- 1R IaC 담당자(서영채, 이소연)가 이 디렉터리 PR의 리뷰어로 참여한다.

## 구조 (예정)

```
infra/
  modules/        재사용 모듈
  envs/
    staging/
    prod/
```
