# AGENTS.md

HacKHU 홈페이지 명세를 읽고 수정할 때 필요한 문서와 작성 규칙을 안내한다. 이 파일의 상대경로는 `docs/spec/` 기준이다.

명세 본문은 이 문서에 복사하지 않는다. 내용은 해당 문서에서 확인한다.

<br>

### 1. 구조와 읽는 순서

| 위치 | 정하는 것 | 작성 |
|---|---|---|
| [PRODUCT.md](PRODUCT.md) | 제품 정의, 첫 버전 범위와 하지 않는 것, 역할, 기능과 담당, 공통 용어 | PM |
| [UI-DESIGN-SYSTEM.md](UI-DESIGN-SYSTEM.md) | 색, 글꼴, 간격, 공용 컴포넌트, 공통 헤더와 메뉴 | PM |
| [DEPLOYMENT.md](DEPLOYMENT.md) | 백엔드와 프론트엔드 배포, AWS 구성, 환경 변수와 시크릿 | PM, PL |
| [DECISIONS.md](DECISIONS.md) | 제품 단위 결정과 PM 결정의 이력 | PM |
| [auth/](auth/AGENTS.md) | 가입, 로그인, 가입 승인, 역할 변경, 내 정보 | 인증권한팀 |
| [board/](board/AGENTS.md) | 공지, 자유게시판, 자료게시판, 첨부파일 | 게시판팀 |
| [gallery/](gallery/AGENTS.md) | 앨범과 사진의 업로드, 조회, 삭제 | 갤러리팀 |
| [home/](home/AGENTS.md) | 메인 화면과 동아리 소개 | 게시판팀 |

- 처음에는 `PRODUCT.md`를 읽고, 작업할 기능 폴더의 `BACKGROUND.md`와 `USER-STORIES.md`를 읽는다. 그다음 `backend/`나 `frontend/` 세트를 1번부터 읽는다.
- 기능 폴더의 `BACKGROUND.md`와 `USER-STORIES.md`는 무엇을 만드는지 정하고 PM이 작성한다. `backend/`와 `frontend/` 세트는 어떻게 만드는지 정하고 각 팀이 작성한다.
- 세트 안에서는 앞 번호의 결정이 뒤 번호의 기준이다. 구현에 맞추려고 앞 번호의 결정을 임의로 바꾸지 않는다. 앞 번호가 바뀌면 영향을 받는 뒤 번호도 같은 PR에서 고친다.
- 문서와 코드가 어긋나면 문서가 기준이다. 결정을 바꾸려면 문서를 먼저 고치고 구현을 맞춘다.

<br>

### 2. 같은 사실을 정하는 곳

같은 사실은 한 곳에서 정하고 다른 문서는 참조한다.

| 사실 | 정하는 곳 |
|---|---|
| 역할의 정의 | [PRODUCT.md](PRODUCT.md) |
| 기능별 권한, 파일 형식과 크기, 개수 제한 | 각 기능의 `USER-STORIES.md` |
| API 계약 | 각 기능의 `backend/4-2-INTERACTION-CONTRACT.md` |
| 현재 사용자와 역할을 확인하는 방법 | [auth/backend/3-1](auth/backend/3-1-DESIGN-SOFTWARE-ARCHITECTURE.md) |
| 로그인 상태와 토큰 보관 위치 | [auth/frontend/4-2](auth/frontend/4-2-QUALITY.md) |
| presigned URL 발급과 만료 처리. 게시판 첨부파일도 이 정의를 쓴다 | [gallery/backend/3-1](gallery/backend/3-1-DESIGN-SOFTWARE-ARCHITECTURE.md) |
| API 공통 형식 | [docs/conventions/api.md](../conventions/api.md) |
| 업로드, 인증, 입력 검증의 공통 규칙 | [docs/security/secure-coding.md](../security/secure-coding.md) |
| 백엔드 공통 코드 규칙 | 레포의 [backend/AGENTS.md](../../backend/AGENTS.md) |
| 프론트엔드 공통 코드 규칙 | 레포의 [frontend/AGENTS.md](../../frontend/AGENTS.md) |

<br>

### 3. 결정 기록

- 결정의 이유와 버린 대안은 `DECISIONS.md`에 남긴다. 명세 문서에는 현재 적용되는 결정만 적는다.
- 루트의 [DECISIONS.md](DECISIONS.md)는 제품 단위 결정과 PM 결정을, 각 세트의 `DECISIONS.md`는 팀의 기술 결정을 기록한다.
- 결정 하나를 표 하나로 기록하고 상태, 날짜, 배경, 채택, 대안, 영향을 적는다.
- 결정이 바뀌면 이전 기록을 지우지 않는다. 새 결정을 덧붙이고 이전 결정의 상태를 바꾼다.

<br>

### 4. 작성 규칙

- `PRODUCT.md`와 기능 폴더의 `BACKGROUND.md`, `USER-STORIES.md`는 제품 설계서다. 처음 보는 사람도 이해하게 자연어로 쓴다. 역할 코드, API, 저장 방식 같은 구현 용어와 일정은 넣지 않는다. 구현 용어는 `backend/`와 `frontend/` 세트에 쓴다.
- 본문은 '한다'체로 쓰고 주체, 조건과 동작을 분명히 쓴다. "적절히 처리한다"처럼 해석을 독자에게 넘기는 표현은 쓰지 않는다.
- 값은 확인할 수 있게 적는다. "적당한 크기"가 아니라 "10MB"처럼 쓴다.
- 결정되지 않은 것은 미정, 확인하지 못한 것은 미확인으로 구분한다. 빈 항목을 추측으로 채우지 않는다.
- 문서 첫머리에 이 문서가 무엇을 정하는지 한두 문장으로 쓴다.
- 문서 제목은 `#`, 절 제목은 `###`로 쓴다. 번호가 있는 문서는 파일 번호 뒤에 순번을 붙이고(`### 3-2-1`), 번호가 없는 문서는 `### 1.`부터 쓴다. 제목 번호는 숫자 세 묶음까지만 쓴다.
- 절 앞에는 빈 줄을 두고 `<br>`을 넣는다. 구획선은 쓰지 않는다.
- em dash와 가운뎃점을 쓰지 않는다. 가운뎃점 대신 쉼표나 '와/과'를 쓴다.
- 문서 참조는 문장을 마침표로 끝낸 뒤 괄호 안에 링크를 넣는다. 예: `역할은 제품 명세에서 정한다. ([PRODUCT.md](../PRODUCT.md))`
- 표는 항목 대조에, 체크리스트는 누락이나 완료 여부 확인에 쓴다. 표 셀은 "~함", "~됨"처럼 짧게 끝낸다.
- 실제 비밀값과 실제 사용자 데이터를 문서에 넣지 않는다.
- 파일이나 절의 제목과 위치를 바꾸면 그 대상을 참조하는 다른 문서의 링크도 고친다.
