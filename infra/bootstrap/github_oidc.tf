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

# PowerUserAccess에는 IAM이 없다. 앱 서버 역할을 만들 수 있게 /hackhu-hp/app/ 경로 안에서만 허용한다.
# 이 경로 밖(이 역할 자신 포함)은 바꿀 수 없어서 스스로 권한을 올리지 못한다.
data "aws_iam_policy_document" "terraform_apply_iam" {
  statement {
    sid = "ManageAppRoles"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:ListRolePolicies",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "iam:PassRole",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:GetInstanceProfile",
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
