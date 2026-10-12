# infra

AWS 인프라 Terraform. S3부터 PM과 PL이 기본 구성을 작성하고, 지원한 팀원이 함께 작성한다. 코드 오너는 [CODEOWNERS](../.github/CODEOWNERS)에 있다.

## 규칙

- AWS 변경은 **이 디렉터리의 PR로만** 한다. 콘솔에서 직접 바꾸지 않는다.
- GitHub Actions의 AWS 접근은 **OIDC**로만 한다. 액세스 키를 발급하지 않는다.
- IAM은 최소 권한. 예산 알림을 설정한다.
- `terraform.tfvars`, `*.tfstate`는 커밋하지 않는다 (`.gitignore`로 막혀 있다). 변수 예시는 `terraform.tfvars.example`에 값 없이 둔다.
- PR에서 CI가 `terraform fmt -check`와 `validate`를 돌리고, Checkov가 설정 취약점을 Code scanning에 보고한다.

## 구조 (예정)

```
infra/
  modules/        재사용 모듈
  envs/
    staging/
    prod/
```
