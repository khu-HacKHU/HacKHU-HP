# 문서

| 문서 | 내용 |
|---|---|
| [conventions/code-style.md](conventions/code-style.md) | 네이밍, 디렉터리, 포매팅 등 공통 코드 컨벤션 |
| [conventions/api.md](conventions/api.md) | API 공통 규약 — URL, 응답·에러 형식, 페이지네이션, 인증 |
| [security/secure-coding.md](security/secure-coding.md) | 시큐어 코딩 규칙 (코드 작성 전 필독) |
| [security/tools.md](security/tools.md) | 보안 도구 구성, 라운드별 도입, 결과 판별 방법 |

기능별 설계 문서(ERD, API 명세, 권한 체계)는 각 팀이 S1 2주차에 이 디렉터리 아래에 추가한다.

```
docs/
  conventions/    전 팀 공통 규칙 (PL 관리)
  security/       보안 규칙과 도구 (PL 관리)
  auth/           인증·권한 팀 — 역할 체계, JWT 구조, API 명세
  board/          게시판 팀 — ERD, API 명세
  gallery/        갤러리 + 인프라 팀 — API 명세, 스테이징 환경 설계
```

> 문서의 최종 수정일·작성자는 `git log <파일경로>`로 확인한다. 문서 안에 손으로 적으면 곧 틀린다.
