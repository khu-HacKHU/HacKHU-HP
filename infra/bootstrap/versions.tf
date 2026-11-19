terraform {
  # S3 백엔드의 use_lockfile(DynamoDB 없는 잠금)이 1.10부터다.
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = "ap-northeast-2"

  default_tags {
    tags = {
      Project   = "hackhu-hp"
      ManagedBy = "terraform"
      Stack     = "bootstrap"
    }
  }
}
