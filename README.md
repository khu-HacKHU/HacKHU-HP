# HacKHU-HP

HacKHU 동아리 홈페이지. 기능을 개발하면서 보안 도구를 라운드별로 도입하고, 완성된 기능을 직접 공격·수정해 보는 교육형 프로젝트다.

## 구조

| 디렉터리 | 내용 | 상태 |
|---|---|---|
| [`frontend/`](frontend/) | 웹 프론트엔드 | 프레임워크 미정 |
| [`backend/`](backend/) | API 서버 | Spring vs Python 미정 |
| [`infra/`](infra/) | Terraform (AWS) | S2부터 작성 |
| [`docs/`](docs/) | 컨벤션, API 규약, 보안 문서 | |
| [`practice/`](practice/) | Juice Shop 보안 도구 실습 환경 | S1 실습용 |

## 팀

| 팀 | 담당 기능 |
|---|---|
| 인증·권한 | 회원가입, 로그인, JWT, OAuth2, 관리자 기능 |
| 게시판 | 공지, 자유·자료 게시판, 첨부파일 |
| 갤러리 + 인프라 | 갤러리 업로드(presigned URL), Terraform, 스테이징·운영 배포, CloudWatch |

PM: 강경현 (@kangkyunghyun) · PL: 손수민 (@sumin0218)

## 일정

| 스프린트 | 기간 | 내용 | 보안 라운드 |
|---|---|---|---|
| S1 | 9/28 ~ 10/11 | 세팅과 설계 | Juice Shop 실습 |
| S2 | 10/12 ~ 11/1 | 개발 1 (중간고사 주 제외) | 1R — SAST, SCA·시크릿, IaC |
| S3 | 11/2 ~ 11/15 | 스테이징과 MVP 완성, 7주차 주말 기능 동결 | 2R — 전 영역 + DAST |
| S4 | 11/16 ~ 11/29 | Exploit과 수정 | 3R — 보안 회귀 테스트 |
| S5 | 11/30 ~ 12/13 | 내부 테스트와 운영 배포 | |

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
- `infra/` 변경은 PM·인프라팀, `.github/` 변경은 PL이 코드 오너로 리뷰한다 ([CODEOWNERS](.github/CODEOWNERS))
