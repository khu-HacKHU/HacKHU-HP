# API 공통 규약

모든 팀의 API가 따르는 형식이다. 기능별 API 명세는 각 팀 문서(`docs/auth/`, `docs/board/`, `docs/gallery/`)에 두고, 여기서는 반복되는 규칙만 정한다.

## 1. URL

- 모든 경로는 `/api/v1`로 시작한다. 헬스체크(`/actuator/health`)와 API 문서 경로(`/v3/api-docs`, `/swagger-ui`)는 예외다.
- 리소스는 kebab-case 복수형 명사로 쓴다. 동사를 경로에 넣지 않는다.
- 관리자 전용 API는 `/api/v1/admin/...` 아래에 둔다. 경로만으로 권한을 판단하지 말고 서버에서 역할을 검사한다.

| 동작 | 메서드 | 경로 | 성공 상태 |
|---|---|---|---|
| 목록 | `GET` | `/api/v1/posts` | 200 |
| 단건 조회 | `GET` | `/api/v1/posts/{postId}` | 200 |
| 생성 | `POST` | `/api/v1/posts` | 201 + `Location` 헤더 |
| 전체 수정 | `PUT` | `/api/v1/posts/{postId}` | 200 |
| 부분 수정 | `PATCH` | `/api/v1/posts/{postId}` | 200 |
| 삭제 | `DELETE` | `/api/v1/posts/{postId}` | 204 |
| 하위 리소스 | `GET` | `/api/v1/posts/{postId}/comments` | 200 |
| 동작 (CRUD로 표현 불가) | `POST` | `/api/v1/admin/users/{userId}/approve` | 200 |

## 2. 요청·응답 본문

- `Content-Type: application/json; charset=utf-8`. 파일은 서버가 받지 않고 presigned URL로 S3에 직접 올린다.
- 필드명은 camelCase.
- 시각은 ISO 8601 UTC 문자열: `"2026-10-12T09:30:00Z"`. 화면 표시에서만 KST로 바꾼다.
- ID는 정수(JSON number, 서버에서는 `Long`)로 통일한다. 순차 ID이므로 **ID를 안다고 접근할 수 있어서는 안 된다** — 모든 단건 API에서 권한을 검사한다.
- 값이 없으면 `null`로 준다. 필드를 빼지 않는다.

### 성공 응답

봉투(`data`) 없이 리소스를 그대로 준다.

```json
{
  "id": 42,
  "title": "10월 정기 세미나 공지",
  "author": { "id": 7, "nickname": "khu_member" },
  "createdAt": "2026-10-12T09:30:00Z"
}
```

### 목록 응답 (페이지네이션)

요청: `GET /api/v1/posts?page=0&size=20&sort=createdAt,desc`

- `page`는 0부터, `size` 기본 20, **최대 100** (서버가 강제한다).
- `sort` 허용 필드는 서버가 화이트리스트로 관리한다. 임의 컬럼명을 그대로 쿼리에 넣지 않는다.

```json
{
  "content": [ { "id": 42, "title": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 135,
  "totalPages": 7
}
```

## 3. 에러 응답

모든 에러는 같은 형식을 쓴다.

```json
{
  "status": 403,
  "code": "POST_FORBIDDEN",
  "message": "게시글을 수정할 권한이 없습니다.",
  "errors": []
}
```

입력 검증 실패는 `errors`에 필드별 사유를 담는다.

```json
{
  "status": 400,
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "errors": [
    { "field": "title", "reason": "1자 이상 100자 이하로 입력해주세요." }
  ]
}
```

| 상태 | 쓰는 때 | code 예 |
|---|---|---|
| 400 | 입력 형식·검증 실패 | `INVALID_INPUT` |
| 401 | 인증 없음, 토큰 만료·위조 | `UNAUTHORIZED`, `TOKEN_EXPIRED` |
| 403 | 인증은 됐지만 권한 없음 | `FORBIDDEN`, `POST_FORBIDDEN` |
| 404 | 리소스 없음 | `POST_NOT_FOUND` |
| 409 | 상태 충돌 (중복 가입 등) | `DUPLICATE_EMAIL` |
| 413 | 업로드 크기 초과 | `FILE_TOO_LARGE` |
| 429 | 요청 과다 | `TOO_MANY_REQUESTS` |
| 500 | 서버 오류 | `INTERNAL_ERROR` |

- `code`는 `UPPER_SNAKE_CASE`, 도메인 접두사를 붙인다 (`AUTH_`, `POST_`, `GALLERY_`). 클라이언트는 `message`가 아니라 `code`로 분기한다.
- **500 응답에 스택 트레이스, SQL, 내부 경로, 라이브러리 버전을 넣지 않는다.** 상세는 서버 로그에만 남긴다.
- 로그인 실패는 "아이디가 없음"과 "비밀번호 틀림"을 구분하지 않는다 (계정 열거 방지).
- 남의 비공개 리소스에 접근하면 존재 여부를 숨기기 위해 403 대신 404를 줄 수 있다. 팀 명세에 어느 쪽인지 적는다.

## 4. 인증

- 인증 방식과 토큰 구조는 인증·권한 팀이 S1 2주차에 `docs/auth/`에 정한다. 이 절은 그 결정에 맞춰 갱신한다.
- 토큰을 URL 쿼리스트링에 넣지 않는다 (로그·Referer로 샌다).
- 인증이 필요 없는 API는 명세에 **"공개"라고 명시**한다. 명시가 없으면 인증 필요가 기본값이다.

## 5. 변경과 호환성

- 응답 필드를 삭제하거나 이름·타입을 바꾸는 것은 호환성을 깨는 변경이다. 프론트엔드와 합의한 뒤 같은 PR에서 명세를 갱신한다.
- 필드 추가는 호환되는 변경이다.
- API 명세는 springdoc-openapi가 코드에서 생성한다. 로컬에서 http://localhost:8080/swagger-ui.html 로 확인하고, 컨트롤러·DTO에 `@Operation`, `@Schema`로 설명을 단다.
