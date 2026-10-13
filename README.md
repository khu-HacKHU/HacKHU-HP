# HacKHU-HP

HacKHU 동아리 홈페이지. 기능을 개발하면서 보안 도구를 스프린트별로 도입하고, 완성된 기능을 직접 공격·수정해 보는 교육형 프로젝트다.

## 구조

| 디렉터리 | 내용 | 상태 |
|---|---|---|
| [`frontend/`](frontend/) | 웹 프론트엔드 | 프레임워크 미정 |
| [`backend/`](backend/) | API 서버 | Spring Boot 4.1 / Java 21 / PostgreSQL 17 |
| [`infra/`](infra/) | Terraform (AWS) | S3부터 작성 |
| [`docs/`](docs/) | 컨벤션, API 규약, 보안 문서 | |
| [`practice/`](practice/) | Juice Shop 보안 도구 실습 환경 | S1 실습용 |

## 팀

| 팀 | 담당 |
|---|---|
| 인증권한 | [인증권한](docs/spec/auth/BACKGROUND.md) |
| 게시판 | [게시판](docs/spec/board/BACKGROUND.md), [홈](docs/spec/home/BACKGROUND.md) |
| 갤러리 | [갤러리](docs/spec/gallery/BACKGROUND.md) |
| PM, PL | [배포와 인프라](docs/spec/DEPLOYMENT.md) |

팀원은 S2에 프론트엔드와 백엔드 희망을 받아 배정한다. 같은 기능의 API 담당과 화면 담당은 같은 팀에 둔다. 기능별 범위는 각 기능의 배경 문서와 [제품 명세](docs/spec/PRODUCT.md)에 있다.

PM: 강경현 (@kangkyunghyun) · PL: 손수민 (@sumin0218)

## 일정

| 스프린트 | 기간 | 내용 | 보안 점검 |
|---|---|---|---|
| S1 | 9/28 ~ 10/11 | 세팅, 교육(10/6 보안 도구, 10/8 Git) | |
| S2 | 10/12 ~ 10/25 | 희망 역할 조사와 배정, 중간고사 | |
| S3 | 10/26 ~ 11/8 | 설계와 개발 시작 | SAST, SCA·시크릿, IaC |
| S4 | 11/9 ~ 11/22 | 스테이징과 MVP 완성, 마지막 주말 기능 동결 | 컨테이너, 배포, 모니터링, DAST 추가 |
| S5 | 11/23 ~ 12/6 | Exploit과 수정, 내부 테스트와 운영 배포 | 보안 회귀 테스트 |

## 시작하기

```bash
git clone https://github.com/khu-HacKHU/HacKHU-HP.git
cd HacKHU-HP
npm install   # 커밋 메시지 검사 훅 설치 (선택)
git switch develop
```

1. [CONTRIBUTING.md](CONTRIBUTING.md) — 브랜치, 커밋, PR 규칙
2. [docs/security/secure-coding.md](docs/security/secure-coding.md) — 코드 작성 전 필독
3. [docs/conventions/api.md](docs/conventions/api.md) — API 공통 규약
4. [practice/juice-shop/](practice/juice-shop/) — 보안 도구 실습

## 협업 규칙

- 모든 이슈와 진행 상황은 GitHub Projects 보드 하나에서 관리한다
- 업무 할당은 GitHub 이슈로 공개적으로 한다. 결정 사항도 이슈에 남긴다
- PR 리뷰는 같은 팀 안에서 한다. 인증·파일 업로드·권한 관련 PR은 PL이 최종 승인한다
- `infra/` 변경은 PM과 PL, `.github/` 변경은 PL이 코드 오너로 리뷰한다 ([CODEOWNERS](.github/CODEOWNERS))
