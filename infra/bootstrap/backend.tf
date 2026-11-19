# 처음 적용할 때는 버킷이 없으므로 이 파일을 잠시 치워 두고 로컬 상태로 만든 뒤 옮긴다. (README.md)
terraform {
  backend "s3" {
    bucket       = "khu-hackhu-hp-tfstate"
    key          = "bootstrap/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
