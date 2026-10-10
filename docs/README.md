# 문서

| 문서 | 내용 |
|---|---|
| [conventions/code-style.md](conventions/code-style.md) | 네이밍, 디렉터리, 포매팅 등 공통 코드 컨벤션 |
| [conventions/api.md](conventions/api.md) | API 공통 규약 — URL, 응답·에러 형식, 페이지네이션, 인증 |
| [security/secure-coding.md](security/secure-coding.md) | 시큐어 코딩 규칙 (코드 작성 전 필독) |
| [security/tools.md](security/tools.md) | 보안 도구 구성, 라운드별 도입, 결과 판별 방법 |
| [spec/](spec/AGENTS.md) | 기능 명세. 제품 정의, 기능별 배경과 사용자 스토리, 팀별 백엔드와 프론트엔드 설계 |

```
docs/
  conventions/    전 팀 공통 규칙 (PL 관리)
  security/       보안 규칙과 도구 (PL 관리)
  spec/           기능 명세. 읽는 순서와 작성 규칙은 spec/AGENTS.md
```

> 문서의 최종 수정일·작성자는 `git log <파일경로>`로 확인한다. 문서 안에 손으로 적으면 곧 틀린다.
