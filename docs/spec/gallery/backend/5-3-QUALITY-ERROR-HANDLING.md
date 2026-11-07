# 5-3 오류 처리

본 문서는 갤러리 기능의 에러 코드와 실패별 처리를 정한다. 응답 형식은 [api.md 3절](../../../conventions/api.md)을 따르고, 코드 구현은 `domain/gallery/exception/GalleryErrorCode`다.

<br>

### 1. 에러 코드

| 코드 | 상태 | 언제 |
|---|---|---|
| `GALLERY_ALBUM_NOT_FOUND` | 404 | 없는 앨범을 조회하거나 지우려 함 |
| `GALLERY_UNSUPPORTED_FORMAT` | 400 | 발급 요청의 `contentType`이 jpg, png, webp의 MIME이 아님 |
| `GALLERY_FILE_TOO_LARGE` | 400 | 발급 요청의 `size`가 10MB를 넘음 |
| `GALLERY_TOO_MANY_REQUESTS` | 429 | 발급 rate limit(분당 5회, 시간당 100장) 초과 |
| `GALLERY_INVALID_UPLOAD` | 400 | 확정 단계 검증 실패. 항목별 사유는 2절 |
| `GALLERY_FUTURE_EVENT_DATE` | 400 | 활동 날짜가 미래 |
| `GALLERY_FORBIDDEN` | 403 | 작성자도 관리자도 아닌 사람이 앨범을 지우려 함 |

공통 코드도 그대로 쓴다. `title` 길이, `eventDate` 누락, 사진 개수(1~20) 위반은 Bean Validation이 걸러서 `INVALID_INPUT`(400)이 된다. 비로그인은 `UNAUTHORIZED`(401)다.

**활동 날짜의 "오늘"은 한국 시간(`Asia/Seoul`) 기준이다.** `@PastOrPresent` 애너테이션을 쓰지 않고 서비스에서 검사하는 이유가 여기 있다. 그 애너테이션은 JVM 기본 시간대로 판단하는데, 서버가 UTC로 돌면 한국 시간 자정부터 오전 9시 사이에는 UTC로 아직 어제라서, 사용자가 자기 기준 오늘 날짜를 골랐는데 미래로 거부당한다. 행사 끝나고 밤에 사진을 올리는 경우가 흔해서 그냥 넘기면 안 되는 구간이다.

<br>

### 2. 확정 실패의 항목별 사유

`GALLERY_INVALID_UPLOAD`는 `errors` 배열에 실패한 항목만 담는다. `field`는 요청 배열의 위치(`uploadIds[2]`)다. 첫 실패에서 끊지 않고 전부 모아서 주는데, 사용자가 하나 고치고 다시 보냈다가 또 걸리는 일을 줄이려는 것이다.

| 사유 문구 | 언제 |
|---|---|
| 본인이 발급받은 업로드 주소가 아닙니다. | 발급 기록에 없거나, 다른 사용자 것이거나, 용도가 갤러리가 아님 |
| 이미 사용했거나 만료된 업로드 주소입니다. | 상태가 `USED`이거나 `expires_at`이 지남 |
| 같은 사진을 두 번 넣을 수 없습니다. | 한 요청에 같은 `uploadId`가 중복 |
| 실제 파일이 jpg, png, webp 형식이 아닙니다. | 매직바이트 불일치. 업로드를 아예 하지 않아 객체가 없는 경우도 포함 |

```json
{
  "status": 400,
  "code": "GALLERY_INVALID_UPLOAD",
  "message": "올린 사진을 확인할 수 없습니다. 다시 올려주세요.",
  "errors": [
    { "field": "uploadIds[2]", "reason": "실제 파일이 jpg, png, webp 형식이 아닙니다." },
    { "field": "uploadIds[5]", "reason": "본인이 발급받은 업로드 주소가 아닙니다." }
  ]
}
```

<br>

### 3. 서버 과실과 클라이언트 과실을 나눈다

확정 단계의 실패를 둘로 나눠서 다르게 처리한다. 둘을 같이 묶으면 S3 장애 때 사용자가 사진 20장을 다시 올려야 한다.

| | 클라이언트 과실 | 서버·인프라 과실 |
|---|---|---|
| 예 | 2절의 사유들 | S3 호출 자체가 실패(장애, 타임아웃) |
| 응답 | 400 `GALLERY_INVALID_UPLOAD` | 500 `INTERNAL_ERROR` |
| 발급 기록 | 소비 처리(`USED`)해서 재사용을 막는다 | `ISSUED`로 남겨 재시도를 허용한다 |
| S3 객체 | 아직 안 쓰인 것은 지운다 | 지우지 않는다 |

재시도 없이 버려진 객체는 만료 후 정리 배치가 치운다 (3-2 4절).

<br>

### 4. 응답에 담지 않는 것

실패 사유에 S3 객체 키, 버킷 이름, 예외 메시지, 스택 트레이스를 싣지 않는다. `uploadId`는 본인이 발급받은 값이라 그대로 돌려줘도 된다. 상세는 서버 로그에만 남긴다 (CWE-209, [secure-coding.md 7절](../../../security/secure-coding.md)).
