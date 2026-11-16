# 4-2 API 계약

공통 형식은 [docs/conventions/api.md](../../../conventions/api.md)를 따른다. 실패 응답 코드는 5-3(미작성, 구현 시 채움)에서 정한다.

<br>

### 1. 앨범 목록 (`GET /api/v1/albums`, 공개)

요청: `?page=0&size=20` (sort 고정값 `eventDate,desc` 외 허용 안 함, 화이트리스트 1개뿐)

```json
{
  "content": [
    {
      "id": 12,
      "title": "10월 정기 세미나",
      "eventDate": "2026-10-18",
      "coverPhotoUrl": "https://...",
      "createdAt": "2026-10-19T09:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

`coverPhotoUrl`은 **CDN(CloudFront) 서명 URL**로 내려준다. 만료시간은 1시간(조회마다 새로 서명해서 내려주는 거라 넉넉하게 잡음). 공개 콘텐츠라도 루트 `AGENTS.md`의 "파일은 presigned URL로만 주고받는다" 규칙을 지키려고 서명을 건다 (1-BACKGROUND.md 3절 참고).

<br>

### 2. 앨범 상세 (`GET /api/v1/albums/{albumId}`, 공개)

```json
{
  "id": 12,
  "title": "10월 정기 세미나",
  "eventDate": "2026-10-18",
  "canDelete": false,
  "photos": [
    { "id": 101, "url": "https://...", "order": 0 },
    { "id": 102, "url": "https://...", "order": 1 }
  ],
  "createdAt": "2026-10-19T09:00:00Z"
}
```

없는 앨범은 404 (`GALLERY_ALBUM_NOT_FOUND`).

**작성자 ID는 응답에 넣지 않고, 대신 `canDelete`를 준다.** 화면에서 삭제 버튼을 보여줄지 판단하려면 "이게 내 앨범인가"를 알아야 하는데, 작성자 ID를 노출하는 대신 서버가 권한을 판단한 결과만 내려주는 방식이다. 작성자 본인이거나 관리자면 `true`, 그 외에는 `false`다. 비로그인이면 항상 `false`다.

- **`canDelete`는 화면 표시용이고 보안 장치가 아니다.** 삭제 API는 `canDelete`와 무관하게 요청마다 서버에서 권한을 다시 검사한다. 프론트에서 버튼을 숨기는 것은 권한 검사가 아니다 (루트 `AGENTS.md`).
- 목록 응답(1절)에는 넣지 않는다. 목록은 사용자와 무관한 공개 응답으로 두어 나중에 캐싱할 여지를 남긴다.
- 구현 시점은 `#59`(앨범 삭제 API)다. 이 필드는 현재 사용자를 알아야 계산되는데, 상세 조회(`#36`)는 지금 블로커 없이 진행 중이라 거기에 의존을 더하지 않는다. 필드 추가는 호환되는 변경이다 ([api.md 5절](../../../conventions/api.md)). 화면 반영은 `#60`(앨범 삭제 화면)에서 한다.

"탈퇴한 회원" 표시 로직(`auth/USER-STORIES.md` 5절)은 게시판 쪽 글 작성자 표시에 필요한 것이고, 갤러리 API는 작성자를 보여주지 않으므로 해당 없음.

<br>

### 3. 업로드 주소 발급 (`POST /api/v1/albums/upload-urls`, 동아리원)

요청:

```json
{
  "files": [
    { "contentType": "image/jpeg", "size": 2048000 },
    { "contentType": "image/png", "size": 1024000 }
  ]
}
```

응답:

```json
{
  "uploads": [
    { "uploadId": "a1b2c3d4-...", "uploadUrl": "https://...", "expiresAt": "2026-11-04T10:10:00Z" },
    { "uploadId": "e5f6a7b8-...", "uploadUrl": "https://...", "expiresAt": "2026-11-04T10:10:00Z" }
  ]
}
```

- `files` 1~20개 아니면 400.
- `contentType`이 jpg/png/webp의 MIME이 아니면 400 (`GALLERY_UNSUPPORTED_FORMAT`).
- `size`(바이트)가 10MB를 넘으면 400 (`GALLERY_FILE_TOO_LARGE`). S3에 아무것도 올리기 전에, 발급 시점에 바로 거부한다.
- **방식은 presigned PUT이다.** 클라이언트는 `uploadUrl`에 `PUT` + 파일 바이트를 그대로 보낸다. 이때 보내는 `Content-Length`는 요청에 적은 `size`와 정확히 같아야 한다. 서명에 그 값이 고정돼 있어서, 실제로 더 큰 파일을 보내면 S3가 서명 불일치로 업로드 자체를 거부한다.
- **만료 시간: 10분.**
- 응답엔 S3 객체 키를 노출하지 않는다. `uploadId`만 내려주고, 실제 키는 서버 내부(`global/storage` 발급기록)에서만 관리한다 (1-BACKGROUND.md "업로드 발급 기록" 참고).

<br>

### 4. 앨범 올리기 (`POST /api/v1/albums`, 동아리원)

요청:

```json
{
  "title": "10월 정기 세미나",
  "eventDate": "2026-10-18",
  "uploadIds": ["a1b2c3d4-...", "e5f6a7b8-..."]
}
```

- `uploadIds` 배열 순서 = 사진 순서, 0번째 = 대표 사진.
- `uploaderId`, `role` 같은 필드는 받지 않는다. 현재 사용자는 인증 정보에서 꺼낸다 (#22 필요).
- 응답 201 + `Location: /api/v1/albums/{id}`, 본문은 2절 상세 응답과 동일한 형태.
- **처리 순서 (전부 이 API 안에서 동기):**
  1. 각 `uploadId`가 발급 기록에 있고, 호출자 본인에게 발급된 것이고, 아직 안 쓰였는지 확인 (소유자 확인, 싸니까 먼저)
  2. 통과한 것만 S3 Range GetObject(첫 12바이트)로 매직바이트 확인. 크기는 발급 시점에 서명으로 이미 고정돼 있어서 여기서 다시 확인할 필요는 없다.
  3. **클라이언트 과실로 실패**(소유자 확인 실패, 만료, 중복, 매직바이트 불일치)하면 **전체 거부**. 아직 안 쓰인 S3 객체를 전부 삭제하고 그 발급 기록을 소비 처리한 뒤, 400 + `errors` 배열(실패한 `uploadId`별 사유)을 준다. 이미 `USED`인 기록의 객체는 살아있는 앨범의 사진이라 지우지 않는다
  4. **S3 호출 자체가 실패**(장애, 타임아웃)하면 확정 실패로 바꾸지 않고 500으로 준다. 발급 기록은 `ISSUED`로 남기고 객체도 지우지 않아 재시도를 허용한다. 서버 잘못으로 사용자가 사진 20장을 다시 올리게 하지 않으려는 것이다. 재시도 없이 버려진 객체는 만료 후 정리 배치가 치운다
  5. 전부 통과하면 발급 기록을 소비 처리하고, 같은 트랜잭션으로 `album`/`photo` insert

<br>

### 5. 앨범 삭제 (`DELETE /api/v1/albums/{albumId}`, 작성자 또는 관리자)

USER-STORIES 3절에 정의돼 있으나 구현은 별도 이슈(#59)다. 계약만 먼저 정한다.

- 성공 204.
- 작성자도 관리자도 아니면 **403**(`GALLERY_FORBIDDEN`). 앨범은 공개 리소스라 존재를 숨길 이유가 없어서, 솔직하게 권한 없음으로 응답한다.
