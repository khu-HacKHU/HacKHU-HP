# 4-2 API 계약

본 문서는 공지 API의 엔드포인트, 요청과 성공 응답 예시, 필수와 선택 필드를 정한다. 공통 형식은 API 규약을 따른다. ([api.md](../../../conventions/api.md)) 실패 응답의 에러 코드 목록은 5-3에서 정하고, 여기서는 프론트엔드가 분기해야 하는 실패만 적는다. 자유게시판과 자료게시판 API는 #31에서 덧붙인다.

<br>

### 4-2-1. 공통

**인증**

- 공개 API는 토큰 없이 부를 수 있다. 토큰을 보내도 응답은 같다.
- 공개가 아닌 API는 `Authorization: Bearer {accessToken}` 헤더로 access 토큰을 받는다. 토큰 처리와 401 뒤 프론트엔드 흐름은 인증권한 계약을 따른다. ([auth 4-2-1](../../auth/backend/4-2-INTERACTION-CONTRACT.md))

**요청 형식**

- 본문이 있는 요청은 `Content-Type: application/json`만 받는다. 다른 형식이면 415를 준다.
- 요청 본문은 이 문서에 적힌 필드만 쓴다. 적히지 않은 필드(`authorId`, `boardType`, `createdAt` 등)를 보내도 무시하고 저장에 쓰지 않는다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))
- 경로의 `{noticeId}`는 1 이상의 정수만 받는다. 숫자가 아니면 400을 준다.

**작성자**

글 응답의 `author`는 아래 형식이다.

```json
{ "id": 7, "name": "홍길동" }
```

- 작성자가 탈퇴했으면 `name`이 `null`이다. 프론트엔드는 `null`을 "탈퇴한 회원"으로 보여 준다.
- 이메일과 역할은 주지 않는다. ([auth 5-1-9](../../auth/backend/5-1-QUALITY-SECURITY.md))
- 프론트엔드는 `author.id`와 내 정보의 `id`, `role`을 비교해 수정과 삭제 버튼을 보일지 정한다. 버튼을 숨기는 것은 편의이고 권한 검사는 서버가 한다. ([auth 4-2-7](../../auth/backend/4-2-INTERACTION-CONTRACT.md))

**본문**

- `body`는 받은 글자를 그대로 저장하고 그대로 준다. 서버는 HTML 태그를 지우거나 바꾸지 않는다.
- 줄바꿈은 `\r\n`과 `\r`을 `\n`으로 바꿔 저장한다.
- 프론트엔드는 `body`를 글자로 출력한다. HTML로 해석하는 출력(`dangerouslySetInnerHTML` 등)을 쓰지 않는다. 줄바꿈은 CSS(`white-space: pre-wrap`)로 보여 준다. ([secure-coding.md](../../../security/secure-coding.md) 4절)

**프론트엔드가 분기하는 실패**

| 상태 | 코드 | 뜻 | 프론트엔드 동작 |
|---|---|---|---|
| 400 | `INVALID_INPUT` | 제목이나 본문이 길이 규칙에 맞지 않음 | `errors`의 필드별 사유를 입력 칸 옆에 보여 줌 |
| 401 | `UNAUTHORIZED` | 로그인이 필요한 API를 토큰 없이 부름, 또는 토큰이 무효 | 인증권한 계약의 401 흐름을 따름 |
| 403 | `FORBIDDEN` | 공지 작성에 필요한 역할이 없음 | 권한 없음 안내 |
| 403 | `POST_FORBIDDEN` | 남의 공지를 고치거나 지우려 함 | 권한 없음 안내 |
| 404 | `POST_NOT_FOUND` | 공지가 없음 | 없는 글이라고 안내하고 목록으로 보냄 |

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
- `sort`가 `createdAt,desc`가 아니면 쿼리를 실행하기 전에 400 `INVALID_INPUT`을 준다. 받은 값을 쿼리에 넘기지 않고, 실제 정렬은 서버가 고정값으로 건다. 갤러리 목록과 같은 방식이다. (gallery 5-3-QUALITY-ERROR-HANDLING.md 5-3-1, PR #107)
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
  "body": "일시: 11월 14일 19시\n장소: 동아리방\n\n발표 주제는 단톡방에 올립니다.",
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
  "body": "일시: 11월 14일 19시\n장소: 동아리방"
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| `title` | 필수 | 앞뒤 공백을 지운 뒤 1자 이상 100자 이하 |
| `body` | 필수 | 1자 이상 10,000자 이하, 공백만으로 된 값은 받지 않음 |

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
