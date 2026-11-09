# 배포

본 문서는 백엔드와 프론트엔드의 배포 단위와 경로, 스테이징과 운영 환경, AWS 리소스 구성, 환경 변수와 시크릿의 주입 경로, 배포 조건, 되돌리기, 로그와 알림을 정한다. 기능별 배포 선행 조건도 여기에 모은다.

Terraform 작업(#38, #39)과 스테이징 첫 배포(#42)가 이 문서를 기준으로 한다. AWS 변경은 `infra/` 디렉터리의 PR로만 한다. ([infra/README.md](../../infra/README.md))

<br>

### 1. 배포 단위와 경로

백엔드 하나와 프론트엔드 하나로 배포한다. 기능별로 나누지 않는다. ([DECISIONS.md](DECISIONS.md) D3)

| 단위 | 빌드 | 저장 | 실행 |
|---|---|---|---|
| 백엔드 | `./gradlew bootJar` 뒤 컨테이너 이미지 | ECR, 태그는 커밋 SHA | ECS Fargate 태스크 1개 |
| 프론트엔드 | `npm run build` | S3 정적 파일 | CloudFront |

<br>

#### 1-1. 주소를 하나로 합친다

CloudFront 배포 하나가 프론트엔드와 API를 모두 받는다. 경로로 갈라서 뒤로 넘긴다.

| 경로 | 뒤 | 캐시 |
|---|---|---|
| `/api/*` | ALB | 끔. 쿠키와 헤더를 모두 전달함 |
| `/photos/*` | 사진 S3 | 10분. 서명 URL만 받음 |
| `/*` | 프론트엔드 S3 | 켬. 403과 404는 `/index.html`로 돌림 |

주소를 합치는 이유는 세 가지다.

1. **HTTPS가 필요하다.** refresh 토큰 쿠키가 `Secure`로 고정되어 있어 HTTP에서는 브라우저가 쿠키를 버린다. 그러면 로그인이 동작하지 않는다. ([auth/backend/5-1-QUALITY-SECURITY.md](auth/backend/5-1-QUALITY-SECURITY.md) 5-1-5)
2. **도메인을 아직 사지 않았다.** ALB 기본 주소에는 인증서가 없고 ACM 인증서는 보유한 도메인에만 발급된다. CloudFront 기본 도메인은 유효한 인증서가 함께 온다.
3. **refresh 쿠키가 `SameSite=Strict`다.** `cloudfront.net`은 Public Suffix List에 있어 CloudFront 배포를 둘 만들면 서로 다른 사이트가 된다. 그러면 쿠키가 실리지 않는다. 배포 하나에 담으면 프론트엔드와 API가 같은 출처가 되어 CORS도 필요하지 않다.

프론트엔드와 API는 어느 환경에서도 **같은 등록 도메인에 둔다.** 도메인을 산 뒤에도 이 제약은 그대로다. `SameSite=Strict`를 바꾸려면 인증권한팀의 결정 기록을 먼저 고친다. ([auth/backend/DECISIONS.md](auth/backend/DECISIONS.md))

<br>

### 2. 환경

스테이징과 운영 두 개를 둔다. Terraform 구성은 `infra/envs/staging/`과 `infra/envs/prod/`로 나눈다.

| 환경 | 배포 시점 | 주소 | 용도 |
|---|---|---|---|
| 스테이징 | `develop` 머지 뒤 자동 | CloudFront 기본 도메인 | S4 MVP 확인, DAST 점검 |
| 운영 | `main` 머지 뒤 수동 승인 | 미정, 도메인 구매 뒤 정함 | S5 내부 테스트와 운영 배포 |

- 스테이징에는 실제 사용자 데이터를 넣지 않는다. 테스트 계정만 만든다.
- 스테이징을 먼저 만든다. 운영 환경 구성은 S5의 별도 이슈다. (#75)
- 운영 환경을 새로 만들지, 스테이징을 승격할지는 정하는 중이다. (9절)

<br>

### 3. AWS 리소스 구성

서울 리전(`ap-northeast-2`)에 둔다.

| 리소스 | 구성 | 이유 |
|---|---|---|
| VPC | 퍼블릭 서브넷 2개, 프라이빗 서브넷 2개. 가용 영역 2개 | ALB와 RDS가 서브넷 그룹에 2개 이상을 요구함 |
| ALB | 퍼블릭 서브넷. 헬스체크 경로는 `/actuator/health` | TLS 종료는 CloudFront가 하므로 HTTP만 받음 |
| ECS Fargate | **퍼블릭 서브넷, 태스크 1개, 공인 IP 할당.** 보안 그룹은 ALB에서 온 연결만 받음 | NAT Gateway를 두지 않으려는 것임. 아래 3-1 |
| ECR | 이미지 저장. 푸시할 때 스캔 켬 | |
| RDS PostgreSQL 17 | **프라이빗 서브넷**, 단일 가용 영역, `db.t4g.micro`, 20GB. 보안 그룹은 앱에서 온 연결만 받음 | 인터넷에서 직접 접근할 수 없음 (#39 완료 조건) |
| S3 (프론트엔드) | 퍼블릭 액세스 차단. CloudFront OAC로만 읽음 | |
| S3 (사진) | 퍼블릭 액세스 차단. CloudFront OAC로만 읽음 | 파일은 presigned URL로만 주고받음 |
| CloudFront | 배포 1개, behavior 3개 (1-1). 사진 behavior에 키 그룹을 걸어 서명 URL을 요구함 | 갤러리 설계가 서명 URL을 전제함 ([gallery/backend/5-1-QUALITY-SECURITY.md](gallery/backend/5-1-QUALITY-SECURITY.md) 4절) |
| Secrets Manager | 시크릿 3개 (4절) | |
| CloudWatch Logs | 앱 로그 (7절) | |
| IAM | GitHub OIDC 공급자, 배포 역할, ECS 태스크 역할 | 액세스 키를 발급하지 않음 (#16) |

<br>

#### 3-1. NAT Gateway를 두지 않는다

앱 태스크를 퍼블릭 서브넷에 두고 공인 IP를 준다. 보안 그룹에서 ALB의 보안 그룹에서 온 연결만 받으므로 인터넷에서 앱에 직접 닿을 수 없다.

NAT Gateway는 트래픽이 없어도 시간당 요금이 붙어, 만들어 두고 잊으면 비용이 조용히 쌓인다. 대신 VPC 엔드포인트를 쓰는 방법도 있으나 인터페이스 엔드포인트가 세 개 필요해 절약 폭이 작다.

Checkov가 공인 IP 할당을 경고한다. 스킵하지 않고 **PR 본문에 위 근거를 적는다.** 스킵 기준은 보안 도구 문서를 따른다. ([docs/security/tools.md](../security/tools.md))

<br>

#### 3-2. 앱 인스턴스는 1개로 고정한다

오토스케일링을 걸지 않는다. 지금 코드가 인스턴스 하나를 전제한다.

- 로그인과 가입의 요청 횟수 제한 카운터가 서버 메모리에 있다. ([auth/backend/5-1-QUALITY-SECURITY.md](auth/backend/5-1-QUALITY-SECURITY.md) 5-1-7)
- 갤러리의 업로드 주소 발급 제한도 같은 방식이다. ([gallery/backend/5-1-QUALITY-SECURITY.md](gallery/backend/5-1-QUALITY-SECURITY.md) 4절)
- 인스턴스를 늘리면 한도가 인스턴스 수만큼 느슨해진다. 늘려야 하면 Redis나 WAF로 먼저 옮긴다. ([DECISIONS.md](DECISIONS.md) D3)

배포할 때 새 태스크와 이전 태스크가 잠깐 함께 뜬다. 그 사이에는 한도가 두 배가 되고 정리 배치가 두 번 돌 수 있다. 정리 배치는 같은 조건으로 지워 겹쳐 돌아도 결과가 같다.

<br>

### 4. 환경 변수와 시크릿

값은 **ECS 태스크 정의로만** 넣는다. 설정 파일에 값을 적지 않는다. 시크릿은 Secrets Manager의 ARN을 태스크 정의의 `secrets`로 참조해서, 값이 태스크 정의에 문자열로 남지 않게 한다.

| 이름 | 종류 | 값의 출처 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 설정 | `prod` |
| `DATABASE_URL`, `DATABASE_USERNAME` | 설정 | Terraform 출력 |
| `DATABASE_PASSWORD` | **시크릿** | Secrets Manager. RDS 관리형 암호 |
| `JWT_SECRET` | **시크릿** | Secrets Manager. Base64로 인코딩한 32바이트 이상 |
| `GALLERY_CDN_PRIVATE_KEY` | **시크릿** | Secrets Manager. 서명용 RSA 개인 키 PEM |
| `GALLERY_CDN_BASE_URL`, `GALLERY_CDN_KEY_PAIR_ID` | 설정 | Terraform 출력 |
| `S3_BUCKET`, `AWS_REGION` | 설정 | Terraform 출력 |
| `FRONTEND_ORIGIN` | 설정 | CloudFront 주소 하나. `*`를 쓰지 않음 |
| `TRUSTED_PROXY_CIDRS` | 설정 | ALB가 있는 서브넷의 CIDR. 쉼표로 여러 개 |
| `TRUSTED_PROXY_HOPS` | 설정 | **`2`.** 아래 4-2 |

- AWS 접근 자격 증명은 환경 변수로 넣지 않는다. ECS 태스크 역할로 받는다.
- GitHub Secrets에는 OIDC 역할 ARN만 둔다. AWS 액세스 키를 넣지 않는다.
- `JWT_SECRET`과 `GALLERY_CDN_PRIVATE_KEY`가 없거나 형식이 틀리면 서버가 시작하지 않는다. 배포 실패로 드러나는 것이 맞다.

<br>

#### 4-1. 시크릿을 바꿀 때

- `JWT_SECRET`을 바꾸면 그 전에 발급한 access 토큰이 모두 401이 된다. 프론트엔드가 refresh로 새 토큰을 받으므로 사용자는 다시 로그인하지 않는다. ([auth/backend/DECISIONS.md](auth/backend/DECISIONS.md) A13)
- `GALLERY_CDN_PRIVATE_KEY`를 바꾸면 그 전에 발급한 사진 주소가 열리지 않는다. 주소 수명이 1시간이므로 1시간 뒤에는 영향이 없다.
- 시크릿을 코드, 설정 파일, 커밋 메시지, PR 본문, 이슈에 적지 않는다.

<br>

#### 4-2. 클라이언트 IP와 홉 수

요청 횟수 제한과 로그가 쓰는 클라이언트 IP는 `X-Forwarded-For`의 오른쪽에서 `TRUSTED_PROXY_HOPS`번째 값이다. 프록시가 CloudFront와 ALB 두 단이므로 **`2`**다.

```
브라우저 -> CloudFront -> ALB -> 앱

X-Forwarded-For:  <브라우저>, <CloudFront 엣지>
                                오른쪽에서 1번째
                  오른쪽에서 2번째
```

이 값이 틀리면 조용히 망가진다.

| 값 | 결과 |
|---|---|
| `1` | 모든 사용자가 CloudFront 엣지 IP 하나로 묶임. 몇 사람이 로그인을 반복하면 나머지가 429를 받음 |
| `2` | 맞음 |
| `3` 이상 | 클라이언트가 보낸 값을 그대로 믿음. 제한을 우회할 수 있음 (CWE-348) |

두 가지를 전제한다. 둘 중 하나라도 깨지면 이 값을 다시 정한다.

- **앱이 ALB에서 온 연결만 받는다.** 보안 그룹으로 보장한다. (3절)
- **ALB의 `X-Forwarded-For` 처리가 기본값이다.** 자기에게 연결한 주소를 오른쪽에 덧붙이는 방식이다. `preserve`로 바꾸면 클라이언트가 보낸 값이 그대로 넘어와 위조할 수 있다.

CloudFront를 떼거나 더 두면 홉 수가 달라진다. 구조를 바꾸는 PR은 이 절을 같은 PR에서 고친다.

스테이징에 처음 올린 뒤 **서로 다른 두 네트워크에서 로그인을 실패시켜 제한이 각각 따로 세는지 확인한다.** 단위 테스트로는 잡히지 않고 실제 배포에서만 드러난다. (#42)

<br>

### 5. 배포 조건

| 대상 | 조건 |
|---|---|
| 스테이징 | `develop`에 머지됨. 필수 검사(`ci-ok`, `pr-title`, `gitleaks`)가 통과함 |
| 운영 | `main`에 머지됨. GitHub Environment 승인 1건을 받음 |
| Terraform | 아래 5-1 |

- 배포 워크플로의 AWS 접근은 OIDC로만 한다. IAM 역할은 이 레포의 `develop`과 `main`에서 실행한 워크플로만 쓸 수 있게 제한한다. (#16)
- DB 마이그레이션은 앱이 시작할 때 Flyway가 실행한다. 배포와 마이그레이션이 같은 동작이다.

<br>

#### 5-1. Terraform을 적용하는 방법

| 시점 | 하는 일 |
|---|---|
| `infra/` PR | `terraform plan`을 GitHub Actions에서 돌려 결과를 PR에 붙임. 읽기 전용 역할을 씀 |
| `develop` 머지 | `terraform apply`를 GitHub Actions에서 OIDC로 돌림 |
| 부트스트랩 | 상태 저장 S3 버킷, OIDC 공급자, 배포 역할만 **PM이 로컬에서 한 번** 적용함 |

로컬 적용을 상시 방식으로 두지 않는다. IAM 사용자 액세스 키가 필요해지고 그것이 전역 금지다. 부트스트랩만 예외인 이유는 OIDC가 아직 없어서다. (#16)

PR에서 `terraform fmt -check`와 `validate`는 이미 CI가 돌린다. Checkov가 설정 취약점을 Code scanning에 보고한다.

<br>

### 6. 되돌리기

| 대상 | 방법 |
|---|---|
| 백엔드 | 이전 태스크 정의 리비전으로 서비스를 갱신함 |
| 프론트엔드 | 이전 빌드를 S3에 다시 올리고 CloudFront 캐시를 무효화함 |
| Terraform | 되돌리는 PR을 올려 `apply`함 |
| **DB 마이그레이션** | **되돌리지 않음.** 아래 6-1 |

<br>

#### 6-1. 마이그레이션은 한 단계 하위 호환으로만 쓴다

Flyway에 되돌리기가 없고, 머지된 마이그레이션 파일은 고치지 않는다. ([backend/AGENTS.md](../../backend/AGENTS.md))

그래서 앱을 되돌릴 수 있으려면 **새 마이그레이션이 이전 버전의 앱에서도 동작해야** 한다. 다음을 지킨다.

| 하려는 것 | 방법 |
|---|---|
| 컬럼 추가 | 기본값을 주거나 `NULL`을 허용해서 한 번에 함 |
| 컬럼 삭제 | 두 번에 나눔. 먼저 앱이 그 컬럼을 쓰지 않게 배포하고, 다음 배포에서 지움 |
| 컬럼 이름 변경 | 새 컬럼 추가, 양쪽 쓰기, 읽기 전환, 예전 컬럼 삭제로 나눔 |
| `NOT NULL` 추가 | 먼저 값을 채우고, 다음 배포에서 제약을 더함 |

한 번에 지우거나 바꾸면 앱을 되돌린 순간 이전 버전이 없는 컬럼을 찾아 500이 난다.

<br>

### 7. 로그와 알림

- 앱 로그는 CloudWatch Logs로 보낸다. 보존 기간은 30일로 둔다.
- 로그에 비밀번호, 토큰, 비밀번호 해시, 입력한 이메일을 남기지 않는다. 공통 규칙을 따른다. ([docs/security/secure-coding.md](../security/secure-coding.md))
- PostgreSQL 오류의 Detail을 로그에 싣지 않는다. 행 값에 개인정보가 들어 있다. 앱 설정에서 이미 끈다.
- 예산 알림은 AWS Budgets로 설정한다. **리소스를 만들기 전에 걸어 둔다.** (#15)
- 오류가 늘 때의 알림은 S4의 별도 이슈다. (#61)
- 인증 실패 감지 로그의 알림 방법은 관측 문서에서 정한다. ([auth/backend/5-4-QUALITY-OBSERVABILITY.md](auth/backend/5-4-QUALITY-OBSERVABILITY.md))

<br>

### 8. 기능별 배포 선행 조건

기능이 스테이징이나 운영에 올라가기 전에 들어가 있어야 하는 것을 모은다. 새 조건이 생기면 그 PR에서 이 절에 더한다.

<br>

#### 8-1. 마이그레이션 번호

같은 번호를 두 PR이 쓰면 늦게 머지하는 쪽이 올린다. ([backend/AGENTS.md](../../backend/AGENTS.md)) 지금 열려 있는 PR에는 PL이 번호를 배정했다.

| 번호 | PR | 테이블 |
|---|---|---|
| `V1` | #106 | `users` |
| `V2` | #107 | `album`, `photo` |
| `V3` | #116 | `upload_record` |
| `V4` | #118 | `login_sessions`, `refresh_tokens` |
| `V5` | #113 | `post` |

번호가 작은 마이그레이션이 나중에 들어오면 이미 서버를 띄운 DB에서 Flyway가 실패한다. 머지 순서를 이 표와 같게 한다.

<br>

#### 8-2. 스테이징을 외부에 열기 전에

- [ ] **인증 API의 요청 횟수 제한이 들어가 있다.** (#25) 가입은 공개 POST이고 bcrypt가 0.3초다. 제한이 없으면 CPU 소모와 이메일 열거가 무제한이 된다 (CWE-770). 인증권한 명세가 이 제한을 이메일 노출의 유일한 완화책으로 적어 두었다. ([auth/backend/5-1-QUALITY-SECURITY.md](auth/backend/5-1-QUALITY-SECURITY.md) 5-1-11)
- [ ] `JWT_SECRET`이 Base64로 인코딩한 32바이트 이상이다.
- [ ] 초기 관리자 비밀번호를 첫 로그인에서 바꿨다. (#23)
- [ ] S3 버킷 두 개가 퍼블릭이 아니다.
- [ ] `TRUSTED_PROXY_HOPS`가 실제 구조와 맞는지 두 네트워크에서 확인했다. (4-2)
- [ ] API 문서(`springdoc`)가 꺼져 있다. 기본값이 꺼짐이고 `prod` 프로파일에서 켜지 않는다.

<br>

### 9. 정하는 중

빈 항목을 추측으로 채우지 않는다. 정해지면 이 절에서 빼고 해당 절에 적는다.

| 항목 | 상태 | 막고 있는 것 |
|---|---|---|
| 월 예산 상한 | 미정. PM과 PL이 정함 | #39 착수 |
| 도메인 구매 시점과 주체 | 미정. PM이 정함 | 운영 배포 (#76). 스테이징은 막지 않음 |
| 운영 환경을 새로 만들지, 스테이징을 승격할지 | 미정. PM이 정함 | #75 |
| AWS 계정 상태, 프리티어 적용 여부, 기존 리소스 | **미확인.** #15에서 확인함 | 비용 추정과 #16 |
| CloudFront VPC origin 사용 여부 | **미확인.** 서울 리전 지원과 비용을 확인함 | 없음. 쓰면 CloudFront와 ALB 사이가 암호화되고 ALB를 공개하지 않아도 됨 |

비용은 아직 추정이다. 계정 상태를 확인한 뒤 숫자를 확정하고, **#39에 착수하기 전에 PL 승인을 받는다.** #16과 #38에서 만드는 리소스(IAM, VPC, 서브넷, 보안 그룹)는 시간당 요금이 없다.
