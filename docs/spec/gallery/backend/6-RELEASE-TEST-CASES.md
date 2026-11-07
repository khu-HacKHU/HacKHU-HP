# 6 테스트 케이스

본 문서는 갤러리 백엔드의 테스트 케이스와 실행 명령, 통과 여부를 정한다. 케이스가 있는 것과 실행해서 통과한 것을 구분해 적는다.

<br>

### 6-1 실행 명령

```bash
cd backend
./gradlew test --tests 'com.hackhu.hp.domain.gallery.*'
```

Docker가 필요하다. 조회 API 테스트는 Testcontainers로 PostgreSQL 17을 띄우고 Flyway 마이그레이션을 그대로 적용한다.

<br>

### 6-2 앨범 목록과 상세 조회 (#36)

테스트 클래스는 `AlbumQueryApiTest`다.

| 케이스 | 기대 | 근거 | 통과 |
|---|---|---|---|
| 로그인하지 않고 목록을 조회함 | 200, 제목과 활동 날짜와 대표 사진 주소가 있음 | USER-STORIES 1절 | 통과 |
| 목록의 대표 사진 | 첫 번째 사진의 서명 URL임 | 4-2 1절 | 통과 |
| 활동 날짜가 다른 앨범 3개 | 최근 날짜가 먼저 옴 | USER-STORIES 1절 | 통과 |
| 활동 날짜가 같은 앨범 2개를 한 장씩 넘김 | 나중에 올린 앨범이 먼저 오고 페이지가 겹치지 않음 | 4-2 1절 | 통과 |
| 앨범이 없음 | 빈 목록 | 4-2 1절 | 통과 |
| `size=1000` | `size`가 100임 | api.md 2절 | 통과 |
| `sort=eventDate,desc` | 200 | 4-2 1절 | 통과 |
| `sort=uploaderId,desc`, `sort=noSuchField` | 400 `INVALID_INPUT` | 5-3-1 | 통과 |
| `sort=eventDate,asc` | 400 `INVALID_INPUT` | 5-3-1 | 통과 |
| 목록과 상세 응답 | `uploaderId`, `objectKey`가 없음 | 4-2 2절, 5-1 4절 | 통과 |
| 로그인하지 않고 상세를 조회함 | 200, 사진이 `order` 오름차순 | USER-STORIES 1절 | 통과 |
| 없는 앨범 | 404 `GALLERY_ALBUM_NOT_FOUND` | 5-3-1 | 통과 |
| 앨범 ID가 숫자가 아님 | 400 `INVALID_INPUT` | 5-3-1 | 통과 |

<br>

### 6-3 조회 주소 서명

테스트 클래스는 `CloudFrontPhotoUrlResolverTest`, `PemPrivateKeysTest`다. 키는 테스트 안에서 새로 만들고 실제 키는 쓰지 않는다.

| 케이스 | 기대 | 통과 |
|---|---|---|
| 객체 키로 주소를 만듦 | CDN 주소 뒤에 객체 키가 붙고 `Expires`, `Signature`, `Key-Pair-Id`가 있음 | 통과 |
| 만료 시각 | 지금부터 1시간 뒤 | 통과 |
| 서명 검증 | canned policy를 공개 키로 검증하면 통과함 | 통과 |
| PKCS#8 PEM | 읽힘 | 통과 |
| 줄바꿈이 `\n` 두 글자로 들어온 PEM | 읽힘 | 통과 |
| PKCS#1 PEM | 변환 방법을 안내하며 거부함 | 통과 |
| 깨진 PEM | 키 내용을 메시지에 넣지 않고 거부함 | 통과 |

<br>

### 6-4 업로드와 삭제

미작성. 권한 거부 케이스(비로그인 401, 승인 대기 회원 403, 남의 앨범 삭제 403)는 #40, #41, #59에서 채운다.
