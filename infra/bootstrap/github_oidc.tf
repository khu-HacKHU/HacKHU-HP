locals {
  repo       = "khu-HacKHU/HacKHU-HP"
  region     = "ap-northeast-2"
  account_id = data.aws_caller_identity.current.account_id
}

data "aws_caller_identity" "current" {}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

# 어느 워크플로가 역할을 쓸 수 있는지는 OIDC 토큰의 sub로 가른다.
# 포크에서 온 PR에는 GitHub이 OIDC 토큰을 주지 않는다.
data "aws_iam_policy_document" "assume_plan" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${local.repo}:pull_request"]
    }
    # PR이 자기 워크플로 파일을 고쳐 역할을 쓰지 못하게, develop에 있는 재사용 워크플로의 잡만 받는다.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:job_workflow_ref"
      values   = ["${local.repo}/.github/workflows/terraform-plan.yml@refs/heads/develop"]
    }
  }
}

data "aws_iam_policy_document" "assume_apply" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${local.repo}:ref:refs/heads/develop"]
    }
  }
}

# PR에서 terraform plan을 돌리는 역할. 읽기만 하고, 상태 잠금 파일만 쓴다.
# plan은 PR의 Terraform 코드를 실행하므로(data "external", provider) 이 역할의 권한은 PR 작성자에게 열려 있다고 본다.
resource "aws_iam_role" "terraform_plan" {
  name                 = "hackhu-hp-terraform-plan"
  assume_role_policy   = data.aws_iam_policy_document.assume_plan.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "terraform_plan_readonly" {
  role       = aws_iam_role.terraform_plan.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

data "aws_iam_policy_document" "terraform_plan_state" {
  statement {
    sid       = "StateLock"
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.tfstate.arn}/*.tflock"]
  }

  # ReadOnlyAccess는 모든 버킷의 객체를 읽게 한다. 상태 버킷 밖(DB 백업 등)은 읽지 못하게 막는다.
  statement {
    sid           = "NoObjectsOutsideState"
    effect        = "Deny"
    actions       = ["s3:GetObject", "s3:GetObjectVersion"]
    not_resources = ["${aws_s3_bucket.tfstate.arn}/*"]
  }

  # 같은 이유로 운영 로그, 배포 명령 출력, 인스턴스 콘솔 출력의 본문도 막는다. refresh에 쓰는 Describe와 List는 남긴다.
  statement {
    sid    = "NoLogData"
    effect = "Deny"
    actions = [
      "logs:GetLogEvents",
      "logs:FilterLogEvents",
      "logs:GetLogRecord",
      "logs:GetQueryResults",
      "logs:StartQuery",
      "logs:StartLiveTail",
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations",
      "ec2:GetConsoleOutput",
      "ec2:GetConsoleScreenshot",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "terraform_plan_state" {
  name   = "tfstate-only"
  role   = aws_iam_role.terraform_plan.id
  policy = data.aws_iam_policy_document.terraform_plan_state.json
}

# develop 머지 뒤 terraform apply를 돌리는 역할.
# PowerUserAccess로 넓게 둔다. 배포 구성(#129)이 정해지면 쓰는 서비스만 남기고 줄인다.
resource "aws_iam_role" "terraform_apply" {
  name                 = "hackhu-hp-terraform-apply"
  assume_role_policy   = data.aws_iam_policy_document.assume_apply.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "terraform_apply_poweruser" {
  role       = aws_iam_role.terraform_apply.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

# 앱 서버 역할이 가질 수 있는 권한의 상한. apply 역할이 만드는 역할에는 이 경계가 반드시 붙는다.
# 경계 없이 관리자 정책을 붙인 역할을 만들어 EC2에 넘기면 apply 역할이 관리자 권한을 얻을 수 있어서다.
data "aws_iam_policy_document" "app_boundary" {
  # AmazonSSMManagedInstanceCore가 주는 범위. 세션 접속과 에이전트 동작에 필요한 것만 있다.
  statement {
    sid = "SsmAgent"
    actions = [
      "ssm:DescribeAssociation",
      "ssm:GetDeployablePatchSnapshotForInstance",
      "ssm:GetDocument",
      "ssm:DescribeDocument",
      "ssm:GetManifest",
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:ListAssociations",
      "ssm:ListInstanceAssociations",
      "ssm:PutInventory",
      "ssm:PutComplianceItems",
      "ssm:PutConfigurePackageResult",
      "ssm:UpdateAssociationStatus",
      "ssm:UpdateInstanceAssociationStatus",
      "ssm:UpdateInstanceInformation",
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
      "ec2messages:AcknowledgeMessage",
      "ec2messages:DeleteMessage",
      "ec2messages:FailMessage",
      "ec2messages:GetEndpoint",
      "ec2messages:GetMessages",
      "ec2messages:SendReply",
    ]
    resources = ["*"]
  }

  # 리소스를 지정할 수 없는 액션이다.
  statement {
    sid = "Unscoped"
    actions = [
      "cloudwatch:PutMetricData",
      "ec2:DescribeTags",
      "ecr:GetAuthorizationToken",
    ]
    resources = ["*"]
  }

  statement {
    sid = "AppBuckets"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket",
    ]
    resources = [
      "arn:aws:s3:::hackhu-hp-*",
      "arn:aws:s3:::hackhu-hp-*/*",
    ]
    # S3 ARN에는 계정이 없어서, 같은 접두사로 남의 계정에 만든 버킷으로 내보내지 못하게 한다.
    condition {
      test     = "StringEquals"
      variable = "s3:ResourceAccount"
      values   = [local.account_id]
    }
  }

  statement {
    sid = "AppLogs"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogStreams",
    ]
    resources = ["arn:aws:logs:${local.region}:${local.account_id}:log-group:/hackhu-hp/*"]
  }

  statement {
    sid       = "AppSecrets"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = ["arn:aws:secretsmanager:${local.region}:${local.account_id}:secret:hackhu-hp/*"]
  }

  # 키는 Secrets Manager나 S3를 거칠 때만 쓴다. 키로 직접 복호화하지 못한다.
  statement {
    sid       = "KmsViaService"
    actions   = ["kms:Decrypt"]
    resources = ["arn:aws:kms:${local.region}:${local.account_id}:key/*"]
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values = [
        "secretsmanager.${local.region}.amazonaws.com",
        "s3.${local.region}.amazonaws.com",
      ]
    }
  }

  statement {
    sid = "AppImages"
    actions = [
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchCheckLayerAvailability",
    ]
    resources = ["arn:aws:ecr:${local.region}:${local.account_id}:repository/hackhu-hp*"]
  }

  statement {
    sid     = "NoTerraformState"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.tfstate.arn,
      "${aws_s3_bucket.tfstate.arn}/*",
    ]
  }
}

resource "aws_iam_policy" "app_boundary" {
  name   = "hackhu-hp-app-boundary"
  path   = "/hackhu-hp/"
  policy = data.aws_iam_policy_document.app_boundary.json
}

# PowerUserAccess에는 IAM이 없다. 앱 서버 역할을 /hackhu-hp/app/ 경로 안에서만, 위 경계를 붙여서만 만들게 한다.
# 이 경로 밖(이 역할 자신, 경계 정책 포함)은 바꿀 수 없어서 스스로 권한을 올리지 못한다.
data "aws_iam_policy_document" "terraform_apply_iam" {
  statement {
    sid = "ChangeAppRolesWithBoundary"
    actions = [
      "iam:CreateRole",
      "iam:PutRolePermissionsBoundary",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
    ]
    resources = ["arn:aws:iam::${local.account_id}:role/hackhu-hp/app/*"]
    condition {
      test     = "StringEquals"
      variable = "iam:PermissionsBoundary"
      values   = [aws_iam_policy.app_boundary.arn]
    }
  }

  statement {
    sid = "ManageAppRoles"
    actions = [
      "iam:DeleteRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:TagInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
    ]
    resources = [
      "arn:aws:iam::${local.account_id}:role/hackhu-hp/app/*",
      "arn:aws:iam::${local.account_id}:instance-profile/hackhu-hp/app/*",
    ]
  }

  statement {
    sid       = "PassAppRolesToEc2Only"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${local.account_id}:role/hackhu-hp/app/*"]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ec2.amazonaws.com"]
    }
  }

  statement {
    sid       = "KeepBoundary"
    effect    = "Deny"
    actions   = ["iam:DeleteRolePermissionsBoundary"]
    resources = ["*"]
  }

  statement {
    sid       = "ReadIam"
    actions   = ["iam:Get*", "iam:List*"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "terraform_apply_iam" {
  name   = "app-iam"
  role   = aws_iam_role.terraform_apply.id
  policy = data.aws_iam_policy_document.terraform_apply_iam.json
}
