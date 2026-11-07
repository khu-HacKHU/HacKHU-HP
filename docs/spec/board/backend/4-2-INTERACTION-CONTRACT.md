# 4-2 API 계약

본 문서는 공지, 자유게시판, 자료게시판과 첨부파일 API의 엔드포인트, 요청과 성공 응답 예시, 필수와 선택 필드를 정한다. 공통 형식은 API 규약을 따른다. ([api.md](../../../conventions/api.md)) 실패 응답의 에러 코드 목록은 5-3에서 정하고, 여기서는 프론트엔드가 분기해야 하는 실패만 적는다.

<br>

### 4-2-1. 공통

**인증**

- 공개 API는 토큰 없이 부를 수 있다. 토큰을 보내도 응답은 같다.
- 공개가 아닌 API는 `Authorization: Bearer {accessToken}` 헤더로 access 토큰을 받는다. 토큰 처리와 401 뒤 프론트엔드 흐름은 인증권한 계약을 따른다. ([auth 4-2-1](../../auth/backend/4-2-INTERACTION-CONTRACT.md))

**요청 형식**

- 본문이 있는 요청은 `Content-Type: application/json`만 받는다. 다른 형식이면 415를 준다.
- 요청 본문은 이 문서에 적힌 필드만 쓴다. 적히지 않은 필드(`authorId`, `boardType`, `createdAt`, `objectKey` 등)를 보내도 무시하고 저장에 쓰지 않는다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))
- 경로의 `{noticeId}`, `{postId}`, `{attachmentId}`는 1 이상의 정수만 받는다. 숫자가 아니면 400을 준다.

**작성자**

글 응답의 `author`는 아래 형식이다.

```json
{ "id": 7, "name": "홍길동" }
```

- 작성자가 탈퇴했으면 `name`이 `null`이다. 프론트엔드는 `null`을 "탈퇴한 회원"으로 보여 준다.
- 이메일과 역할은 주지 않는다. ([auth 5-1-9](../../auth/backend/5-1-QUALITY-SECURITY.md))
- 프론트엔드는 `author.id`와 내 정보의 `id`, `role`을 비교해 수정과 삭제 버튼을 보일지 정한다. 버튼을 숨기는 것은 편의이고 권한 검사는 서버가 한다. ([auth 4-2-7](../../auth/backend/4-2-INTERACTION-CONTRACT.md))

**본문**

- `content`는 받은 글자를 그대로 저장하고 그대로 준다. 서버는 HTML 태그를 지우거나 바꾸지 않는다.
- 줄바꿈은 `\r\n`과 `\r`을 `\n`으로 바꿔 저장한다.
- 프론트엔드는 `content`를 글자로 출력한다. HTML로 해석하는 출력(`dangerouslySetInnerHTML` 등)을 쓰지 않는다. 줄바꿈은 CSS(`white-space: pre-wrap`)로 보여 준다. ([secure-coding.md](../../../security/secure-coding.md) 4절)

**프론트엔드가 분기하는 실패**

| 상태 | 코드 | 뜻 | 프론트엔드 동작 |
|---|---|---|---|
| 400 | `INVALID_INPUT` | 제목, 본문, 파일 이름이 규칙에 맞지 않음 | `errors`의 필드별 사유를 입력 칸 옆에 보여 줌 |
| 400 | `POST_UNSUPPORTED_FORMAT` | 허용하지 않는 첨부 형식 | 허용 형식(pdf, docx, pptx, xlsx)을 안내함 |
| 400 | `POST_FILE_TOO_LARGE` | 20MB를 넘는 첨부 | 크기 제한을 안내함 |
| 400 | `POST_TOO_MANY_ATTACHMENTS` | 글 하나에 첨부가 5개를 넘음 | 최대 5개라고 안내함 |
| 400 | `POST_INVALID_UPLOAD` | 업로드를 확인할 수 없음 (만료, 이미 씀, 내 업로드가 아님, 올린 파일의 형식이 신고와 다름) | 해당 파일을 다시 올리라고 안내함 |
| 401 | `UNAUTHORIZED` | 로그인이 필요한 API를 토큰 없이 부름, 또는 토큰이 무효 | 인증권한 계약의 401 흐름을 따름 <br> 자유게시판, 자료게시판 화면이면 로그인 안내를 보여 줌 |
| 403 | `FORBIDDEN` | 역할이 부족함 (공지 쓰기를 운영진이 아닌 사람이 함, 승인 대기 회원이 자유게시판이나 자료게시판을 엶) | 승인 대기 회원이면 승인 대기 안내, 그 밖에는 권한 없음 안내 |
| 403 | `POST_FORBIDDEN` | 남의 글을 고치거나 지우려 함 | 권한 없음 안내 |
| 404 | `POST_NOT_FOUND` | 글이 없음, 또는 다른 게시판의 글 | 없는 글이라고 안내하고 목록으로 보냄 |
| 404 | `POST_ATTACHMENT_NOT_FOUND` | 그 글에 그 첨부파일이 없음 | 상세를 다시 불러옴 |
| 429 | `TOO_MANY_REQUESTS` | 업로드 주소나 다운로드 주소를 너무 자주 요청함 | `Retry-After` 초만큼 기다리라고 안내함 |

- 승인 대기 회원 안내와 권한 없음 안내는 응답 코드가 같다. 프론트엔드는 내 정보의 `role`이 `PENDING`인지로 나눈다. ([auth 4-2-7](../../auth/backend/4-2-INTERACTION-CONTRACT.md))
- 첨부 형식과 크기 실패는 업로드 주소를 발급할 때 신고한 값으로 판단하므로 400을 준다. S3에 아무것도 올리기 전에 거부한다. 갤러리와 같은 방식이다. ([gallery 4-2](../../gallery/backend/4-2-INTERACTION-CONTRACT.md) 3절)

에러 코드의 최종 목록은 5-3에서 정한다. 5-3은 미작성이고 구현하면서 채운다.

<br>

### 4-2-2. 목록

인증 수준과 권한 검사는 보안 문서가 기준이다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))

| 기능 | 메서드와 경로 | 인증 수준 | 성공 | 이슈 |
|---|---|---|---|---|
| 공지 목록 | `GET /api/v1/notices` | 공개 | 200 | #29 |
| 공지 상세 | `GET /api/v1/notices/{noticeId}` | 공개 | 200 | #29 |
| 공지 작성 | `POST /api/v1/notices` | 운영진 | 201 | #32 |
| 공지 수정 | `PUT /api/v1/notices/{noticeId}` | 운영진 | 200 | #32 |
| 공지 삭제 | `DELETE /api/v1/notices/{noticeId}` | 운영진 | 204 | #32 |
| 자유게시판 목록 | `GET /api/v1/free-posts` | 동아리원 | 200 | #53 |
| 자유게시판 상세 | `GET /api/v1/free-posts/{postId}` | 동아리원 | 200 | #53 |
| 자유게시판 작성 | `POST /api/v1/free-posts` | 동아리원 | 201 | #53 |
| 자유게시판 수정 | `PUT /api/v1/free-posts/{postId}` | 동아리원 | 200 | #53 |
| 자유게시판 삭제 | `DELETE /api/v1/free-posts/{postId}` | 동아리원 | 204 | #53 |
| 자료게시판 목록 | `GET /api/v1/archive-posts` | 동아리원 | 200 | #55 |
| 자료게시판 상세 | `GET /api/v1/archive-posts/{postId}` | 동아리원 | 200 | #55 |
| 자료게시판 작성 | `POST /api/v1/archive-posts` | 동아리원 | 201 | #55 |
| 자료게시판 수정 | `PUT /api/v1/archive-posts/{postId}` | 동아리원 | 200 | #55 |
| 자료게시판 삭제 | `DELETE /api/v1/archive-posts/{postId}` | 동아리원 | 204 | #55 |
| 첨부 업로드 주소 발급 | `POST /api/v1/archive-posts/upload-urls` | 동아리원 | 200 | #55 |
| 첨부 다운로드 주소 발급 | `POST /api/v1/archive-posts/{postId}/attachments/{attachmentId}/download-url` | 동아리원 | 200 | #55 |

게시판마다 경로를 따로 둔다. 이유와 대안은 결정 기록에 있다. ([DECISIONS.md](DECISIONS.md) B2)

<br>

### 4-2-3. 공지 목록

`GET /api/v1/notices?page=0&size=20`. 공개.

| 쿼리 | 필수 | 설명 |
|---|---|---|
| `page` | 선택 | 0 이상의 정수 <br> 없으면 0 |
| `size` | 선택 | 1 이상의 정수 <br> 없으면 20, 20을 넘으면 20으로 맞춤 |
| `sort` | 선택 | 보내지 않거나 `createdAt,desc`만 받음 |

규칙은 요구사항 문서를 따른다. ([2-REQUIREMENTS.md](2-REQUIREMENTS.md) 2-2)

- 공지 목록 화면은 `size`를 보내지 않는다. 메인 화면의 최근 공지는 `size=5`로 부른다. (홈 USER-STORIES.md 1절, PR #102)
- `page`가 음수이거나, `size`가 1보다 작거나, 둘 중 하나가 숫자가 아니면 400 `INVALID_INPUT`을 준다.
- `sort`가 `createdAt,desc`가 아니면 쿼리를 실행하기 전에 400 `INVALID_INPUT`을 준다. 받은 값을 쿼리에 넘기지 않고, 실제 정렬은 서버가 `created_at DESC, id DESC`로 고정해 건다. `id DESC`가 있어야 작성 시각이 같은 글이 페이지를 넘길 때 겹치거나 빠지지 않는다. 한 트랜잭션에서 넣은 글은 `now()`가 같아 작성 시각이 같아진다. 갤러리 목록과 같은 방식이다. (gallery 5-3-QUALITY-ERROR-HANDLING.md 5-3-1, PR #107)
- 마지막 페이지를 넘으면 `content`가 빈 배열인 200을 준다.
- 응답의 `size`는 실제로 적용한 페이지 크기다.

```json
{
  "content": [
    {
      "id": 42,
      "title": "11월 정기 세미나 안내",
      "author": { "id": 7, "name": "홍길동" },
      "createdAt": "2026-11-07T09:30:00Z",
      "updatedAt": "2026-11-07T09:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

- 목록에는 본문을 넣지 않는다.
- 공지가 하나도 없으면 `content`가 빈 배열이고 `totalElements`가 0이다. 프론트엔드는 빈 목록 안내를 보여 준다.

<br>

### 4-2-4. 공지 상세

`GET /api/v1/notices/{noticeId}`. 공개.

```json
{
  "id": 42,
  "title": "11월 정기 세미나 안내",
  "content": "일시: 11월 14일 19시\n장소: 동아리방\n\n발표 주제는 단톡방에 올립니다.",
  "author": { "id": 7, "name": "홍길동" },
  "createdAt": "2026-11-07T09:30:00Z",
  "updatedAt": "2026-11-07T10:05:00Z"
}
```

| 필드 | 설명 |
|---|---|
| `updatedAt` | 마지막 수정 시각 <br> 수정한 적이 없으면 `createdAt`과 같음 <br> 프론트엔드는 `updatedAt`이 `createdAt`보다 늦으면 수정된 글로 표시함 |

- 공지가 없으면 404 `POST_NOT_FOUND`를 준다.
- 다른 게시판의 글 ID로 부르면 공지가 없는 것과 같게 404를 준다. 다른 게시판 글의 존재 여부를 알려 주지 않는다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))

<br>

### 4-2-5. 공지 작성

`POST /api/v1/notices`. 운영진.

```json
{
  "title": "11월 정기 세미나 안내",
  "content": "일시: 11월 14일 19시\n장소: 동아리방"
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| `title` | 필수 | 앞뒤 공백을 지운 뒤 1자 이상 100자 이하 |
| `content` | 필수 | 1자 이상 10,000자 이하, 공백만으로 된 값은 받지 않음 |

길이 규칙은 요구사항 문서를 따른다. ([2-REQUIREMENTS.md](2-REQUIREMENTS.md) 2-1)

응답은 `201 Created`와 `Location: /api/v1/notices/{noticeId}` 헤더, 본문은 4-2-4의 상세 응답과 같다.

- 작성자는 access 토큰의 사용자로 정한다. 요청 본문으로 받지 않는다.
- 로그인하지 않았으면 401, 운영진이 아니면 403 `FORBIDDEN`을 준다.

<br>

### 4-2-6. 공지 수정

`PUT /api/v1/notices/{noticeId}`. 운영진이면서 작성자, 또는 관리자.

요청 본문과 필드 규칙은 4-2-5와 같다. 제목과 본문을 모두 보내야 한다. 바꾸지 않는 값도 원래 값을 그대로 보낸다.

응답은 `200 OK`이고 본문은 수정된 4-2-4의 상세 응답이다.

- 공지가 없으면 404 `POST_NOT_FOUND`를 준다.
- 운영진이 아니면 403 `FORBIDDEN`, 운영진이지만 작성자도 관리자도 아니면 403 `POST_FORBIDDEN`을 준다.
- 작성자와 작성 시각은 바뀌지 않는다. 관리자가 남의 공지를 고쳐도 작성자는 원래 작성자다.

<br>

### 4-2-7. 공지 삭제

`DELETE /api/v1/notices/{noticeId}`. 운영진이면서 작성자, 또는 관리자.

응답은 `204 No Content`이고 본문이 없다.

- 공지가 없으면 404 `POST_NOT_FOUND`를 준다. 이미 지운 공지를 다시 지워도 404다.
- 권한 실패는 4-2-6과 같다.
- 지운 공지는 복구할 수 없다. 프론트엔드는 지우기 전에 확인 창을 띄운다. (#33)

<br>

### 4-2-8. 자유게시판

`/api/v1/free-posts`. 요청, 응답, 필드 규칙은 공지(4-2-3~4-2-7)와 같고 아래만 다르다.

| 항목 | 공지 | 자유게시판 |
|---|---|---|
| 경로 변수 | `{noticeId}` | `{postId}` |
| 목록, 상세 | 공개 | 동아리원 |
| 작성 | 운영진 | 동아리원 |
| 수정, 삭제 | 운영진이면서 작성자, 또는 관리자 | 작성자, 또는 관리자 |
| `Location` | `/api/v1/notices/{noticeId}` | `/api/v1/free-posts/{postId}` |

- 로그인하지 않았으면 모든 자유게시판 API가 401, 승인 대기 회원이면 403 `FORBIDDEN`이다. 글을 찾기 전에 막으므로 없는 글 ID로 불러도 같은 응답이다. 동아리원이 아닌 사람에게 글이 있는지 알려 주지 않는다. (USER-STORIES.md 3절)
- 동아리원이지만 작성자도 관리자도 아니면 수정, 삭제는 403 `POST_FORBIDDEN`이다.
- 다른 게시판의 글 ID로 부르면 404 `POST_NOT_FOUND`다.

<br>

### 4-2-9. 자료게시판

`/api/v1/archive-posts`. 인증 수준, 권한 실패, 경로 규칙은 자유게시판(4-2-8)과 같다. 글에 첨부파일이 붙는 점만 다르다.

**목록** `GET /api/v1/archive-posts?page=0&size=20`

쿼리 규칙은 공지 목록(4-2-3)과 같다. 항목에 `attachmentCount`가 더해진다.

```json
{
  "content": [
    {
      "id": 77,
      "title": "웹 해킹 스터디 3주차 자료",
      "author": { "id": 12, "name": "김동아" },
      "attachmentCount": 2,
      "createdAt": "2026-11-20T11:00:00Z",
      "updatedAt": "2026-11-20T11:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

**상세** `GET /api/v1/archive-posts/{postId}`

```json
{
  "id": 77,
  "title": "웹 해킹 스터디 3주차 자료",
  "content": "발표 자료와 실습 문제입니다.",
  "author": { "id": 12, "name": "김동아" },
  "attachments": [
    {
      "id": 301,
      "fileName": "3주차_XSS.pdf",
      "contentType": "application/pdf",
      "size": 1834201
    },
    {
      "id": 302,
      "fileName": "실습문제.docx",
      "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "size": 48213
    }
  ],
  "createdAt": "2026-11-20T11:00:00Z",
  "updatedAt": "2026-11-20T11:00:00Z"
}
```

| 필드 | 설명 |
|---|---|
| `attachments` | 표시 순서대로 줌 <br> 첨부가 없으면 빈 배열 |
| `attachments[].fileName` | 원래 파일 이름 <br> 프론트엔드는 글자로 출력함 |
| `attachments[].size` | 바이트 |

- 다운로드 주소와 객체 키는 상세 응답에 넣지 않는다. 받을 때 4-2-11로 따로 발급한다. ([DECISIONS.md](DECISIONS.md) B6)

**작성** `POST /api/v1/archive-posts`

```json
{
  "title": "웹 해킹 스터디 3주차 자료",
  "content": "발표 자료와 실습 문제입니다.",
  "uploadIds": ["9b1d2c3e-...", "4f5a6b7c-..."]
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| `title`, `content` | 필수 | 공지 작성(4-2-5)과 같음 |
| `uploadIds` | 선택 | 4-2-10에서 받은 `uploadId` 0개 이상 5개 이하 <br> 배열 순서가 첨부 표시 순서 <br> 같은 값이 두 번 나오면 400 |

응답은 `201 Created`와 `Location: /api/v1/archive-posts/{postId}` 헤더, 본문은 상세 응답과 같다.

- 서버는 `uploadIds` 하나하나가 요청한 사람에게 첨부 용도로 발급되었고, 아직 쓰이지 않았고, 만료되지 않았는지 확인한다. 그다음 S3에 올라온 파일의 형식을 확인한다. 하나라도 실패하면 글을 만들지 않고 400 `POST_INVALID_UPLOAD`와 `errors`(실패한 `uploadId`별 사유)를 준다. (5-1-7, 5-1-8)
- 실패해서 거부된 업로드는 다시 쓸 수 없다. 프론트엔드는 그 파일을 다시 올려 새 `uploadId`를 받는다.

**수정** `PUT /api/v1/archive-posts/{postId}`

```json
{
  "title": "웹 해킹 스터디 3주차 자료 (수정)",
  "content": "실습 문제를 바꿨습니다.",
  "attachmentIds": [301],
  "uploadIds": ["a1b2c3d4-..."]
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| `title`, `content` | 필수 | 공지 작성(4-2-5)과 같음 |
| `attachmentIds` | 필수 | 이 글에 남길 기존 첨부파일 ID <br> 빈 배열이면 기존 첨부를 모두 뺌 |
| `uploadIds` | 선택 | 새로 붙일 `uploadId` |

- `attachmentIds`에 없는 기존 첨부는 지운다. 바꾸지 않을 때도 기존 첨부 ID를 모두 보낸다.
- 표시 순서는 `attachmentIds` 순서 다음에 `uploadIds` 순서다.
- `attachmentIds`와 `uploadIds`를 합쳐 5개를 넘으면 400 `POST_TOO_MANY_ATTACHMENTS`다.
- `attachmentIds`에 이 글의 첨부가 아닌 ID가 있으면 400 `INVALID_INPUT`이다. 다른 글의 첨부를 옮겨 오지 못하게 한다.
- `uploadIds` 확인은 작성과 같다.
- 응답은 `200 OK`이고 본문은 수정된 상세 응답이다.

**삭제** `DELETE /api/v1/archive-posts/{postId}`

응답은 `204 No Content`다. 첨부파일도 함께 지운다. 이미 발급한 다운로드 주소는 파일이 지워져 열리지 않는다.

<br>

### 4-2-10. 첨부 업로드 주소 발급

`POST /api/v1/archive-posts/upload-urls`. 동아리원.

요청:

```json
{
  "files": [
    { "fileName": "3주차_XSS.pdf", "contentType": "application/pdf", "size": 1834201 }
  ]
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| `files` | 필수 | 1개 이상 5개 이하 |
| `files[].fileName` | 필수 | 1자 이상 200자 이하 <br> 확장자가 `.pdf`, `.docx`, `.pptx`, `.xlsx` 중 하나 (대소문자 무시) |
| `files[].contentType` | 필수 | 아래 표의 MIME 타입 중 하나, 확장자와 짝이 맞아야 함 |
| `files[].size` | 필수 | 1 이상 20,971,520 이하 (바이트) |

| 확장자 | MIME 타입 |
|---|---|
| `.pdf` | `application/pdf` |
| `.docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| `.pptx` | `application/vnd.openxmlformats-officedocument.presentationml.presentation` |
| `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |

응답:

```json
{
  "uploads": [
    {
      "uploadId": "9b1d2c3e-...",
      "uploadUrl": "https://...",
      "expiresAt": "2026-11-20T11:10:00Z"
    }
  ]
}
```

- 형식이 맞지 않으면 400 `POST_UNSUPPORTED_FORMAT`, 크기가 20MB를 넘으면 400 `POST_FILE_TOO_LARGE`다. 발급 전에 거부한다.
- 방식은 presigned PUT이다. 클라이언트는 `uploadUrl`에 `PUT`으로 파일 바이트를 그대로 보낸다. 이때 `Content-Type`은 요청에 적은 `contentType`, `Content-Length`는 요청에 적은 `size`와 정확히 같아야 한다. 둘 다 서명에 들어가 있어서 다르면 S3가 거부한다.
- 업로드 주소는 10분 뒤 만료된다. (2-REQUIREMENTS.md 2-3)
- 응답에 객체 키를 넣지 않는다. 클라이언트는 `uploadId`만 다룬다.
- 이 단계에서는 글과 연결되지 않는다. 글 작성이나 수정에서 `uploadIds`로 보내야 첨부가 된다.
- 요청 횟수 제한을 넘으면 429다. (2-REQUIREMENTS.md 2-5)

**첨부 수 제한을 어디서 막는가**

| 위치 | 막는 것 |
|---|---|
| 프론트엔드, 파일을 고를 때 | 이 글에 남을 첨부와 새로 고른 파일을 합쳐 5개를 넘으면 고르지 못하게 하고 최대 5개라고 안내함 <br> 업로드 주소를 요청하기 전에 막음 |
| 업로드 주소 발급 | 한 번에 5개까지만 발급함 |
| 글 작성, 수정 (확정) | 남길 첨부와 새 업로드를 합쳐 5개를 넘으면 400 `POST_TOO_MANY_ATTACHMENTS` (4-2-9) |

- 업로드 주소 발급은 글의 기존 첨부 수를 세지 않는다. 새 글은 아직 글이 없어 셀 대상이 없고, 수정에서는 5개 중 하나를 바꾸려면 새 파일을 먼저 올리고 저장할 때 옛 파일을 빼야 하기 때문이다. 발급에서 막으면 파일 교체를 할 수 없다.
- 그래서 서버가 확실히 막는 곳은 확정 단계다. 확정에서 거부된 파일이 S3에 올라가 있어도 글에 붙지 않고, 확정하지 않은 업로드는 정리 작업이 지운다. (2-REQUIREMENTS.md 2-7)
- 사용자 스토리의 "6번째 파일을 첨부하려 함 → 올라가지 않음"은 프론트엔드가 고르는 단계에서 막아 맞춘다. (USER-STORIES.md 4절)

<br>

### 4-2-11. 첨부 다운로드 주소 발급

`POST /api/v1/archive-posts/{postId}/attachments/{attachmentId}/download-url`. 동아리원. 요청 본문은 없다.

응답:

```json
{
  "downloadUrl": "https://...",
  "expiresAt": "2026-11-20T11:10:00Z"
}
```

- 방식은 presigned GET이다. 프론트엔드는 받은 주소로 바로 이동하거나 새 창으로 연다. 주소는 10분 뒤 만료된다. (USER-STORIES.md 4절)
- 응답 파일은 `Content-Disposition: attachment`로 내려와 브라우저가 열지 않고 내려받는다. 내려받는 파일 이름은 원래 파일 이름이다. (5-1-9)
- `postId`가 자료게시판 글이 아니면 404 `POST_NOT_FOUND`, 그 글에 `attachmentId`가 없으면 404 `POST_ATTACHMENT_NOT_FOUND`다. 다른 글의 첨부 ID를 넣어도 내려받을 수 없다.
- 주소가 브라우저 기록이나 로그에 남는 것을 줄이려고 `GET`이 아니라 `POST`로 발급한다. 상세 화면을 열 때마다 주소가 발급되지 않고, 사용자가 받기를 누를 때만 발급된다.
- 요청 횟수 제한을 넘으면 429다. (2-REQUIREMENTS.md 2-5)
