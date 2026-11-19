# bootstrap

GitHub Actions가 Terraform을 돌리기 전에 있어야 하는 것을 만든다. PM이 로컬에서 **한 번만** 적용한다. ([DEPLOYMENT.md](../../docs/spec/DEPLOYMENT.md) 5-1, #16)

| 리소스 | 이름 | 쓰는 곳 |
|---|---|---|
| S3 버킷 | `khu-hackhu-hp-tfstate` | 모든 Terraform 상태 |
| OIDC 공급자 | `token.actions.githubusercontent.com` | GitHub Actions의 AWS 접근 |
| IAM 역할 | `hackhu-hp-terraform-plan` | PR에서 `terraform plan`. 읽기 전용 |
| IAM 역할 | `hackhu-hp-terraform-apply` | `develop` 머지 뒤 `terraform apply` |
| IAM 정책 | `hackhu-hp-app-boundary` | 앱 서버 역할의 권한 상한. apply 역할이 만드는 역할에 반드시 붙인다 |

## 처음 적용하는 방법

오래 쓰는 액세스 키를 만들지 않는다. IAM Identity Center의 관리자 권한으로 받은 단기 자격 증명을 쓴다.

```bash
aws configure sso          # 프로필 이름 예: hackhu-admin
aws sso login --profile hackhu-admin
export AWS_PROFILE=hackhu-admin
aws sts get-caller-identity  # 동아리 계정인지 확인

cd infra/bootstrap

# 1. 버킷이 아직 없으므로 백엔드 설정을 치워 두고 로컬 상태로 만든다
mv backend.tf backend.tf.off
terraform init
terraform plan -out=tfplan
terraform apply tfplan

# 2. 버킷이 생겼으니 백엔드를 되돌리고 로컬 상태를 버킷으로 옮긴다
mv backend.tf.off backend.tf
terraform init -migrate-state
rm -f terraform.tfstate terraform.tfstate.backup tfplan
```

`-migrate-state`가 버킷에 이미 상태가 있다고 하면 덮어쓰지 말고 멈춘다. 이전에 적용하다 만 흔적일 수 있으니 버킷의 상태와 로컬 상태를 비교한 뒤 정한다.

끝나면 출력된 두 역할 ARN을 #16에 적는다. 역할 ARN은 시크릿이 아니다.

## 그 뒤에 바꿀 때

이 디렉터리도 PR로 바꾸지만 적용은 PM이 로컬에서 같은 방법으로 한다. apply 역할이 자기 자신, OIDC 공급자, 경계 정책을 바꿀 수 없게 막아 두었기 때문이다.

앱 서버 역할은 `/hackhu-hp/app/` 경로에 만들고 `permissions_boundary`에 `app_boundary_policy_arn`을 넣는다. 빠지면 apply가 권한 오류로 멈춘다. 앱에 경계 밖의 권한이 필요하면 경계 정책을 이 디렉터리에서 PR로 넓힌다.
