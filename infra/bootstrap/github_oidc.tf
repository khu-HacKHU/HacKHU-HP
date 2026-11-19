locals {
  repo = "khu-HacKHU/HacKHU-HP"
}

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
resource "aws_iam_role" "terraform_plan" {
  name                 = "hackhu-hp-terraform-plan"
  assume_role_policy   = data.aws_iam_policy_document.assume_plan.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "terraform_plan_readonly" {
  role       = aws_iam_role.terraform_plan.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

data "aws_iam_policy_document" "tfstate_lock" {
  statement {
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.tfstate.arn}/*.tflock"]
  }
}

resource "aws_iam_role_policy" "terraform_plan_lock" {
  name   = "tfstate-lock"
  role   = aws_iam_role.terraform_plan.id
  policy = data.aws_iam_policy_document.tfstate_lock.json
}

# develop 머지 뒤 terraform apply를 돌리는 역할.
# ponytail: PowerUserAccess로 넓게 둔다. 배포 구성(#129)이 정해지면 쓰는 서비스만 남기고 줄인다.
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
  statement {
    sid = "AppServer"
    actions = [
      "ssm:*",
      "ssmmessages:*",
      "ec2messages:*",
      "logs:*",
      "cloudwatch:PutMetricData",
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket",
      "secretsmanager:GetSecretValue",
      "kms:Decrypt",
      "ecr:GetAuthorizationToken",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchCheckLayerAvailability",
      "ec2:DescribeTags",
    ]
    resources = ["*"]
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
    resources = ["arn:aws:iam::*:role/hackhu-hp/app/*"]
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
      "iam:UpdateAssumeRolePolicy",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:TagInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
    ]
    resources = [
      "arn:aws:iam::*:role/hackhu-hp/app/*",
      "arn:aws:iam::*:instance-profile/hackhu-hp/app/*",
    ]
  }

  statement {
    sid       = "PassAppRolesToEc2Only"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::*:role/hackhu-hp/app/*"]
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
