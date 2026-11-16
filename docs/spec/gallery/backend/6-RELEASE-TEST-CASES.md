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
| 목록의 사진 수 | 앨범마다 `photoCount`가 실제 사진 수와 같음 (#59) | 4-2 1절 | 통과 |
| 비로그인으로 상세를 조회함 | `canDelete`가 `false` (#59) | 4-2 2절 | 통과 |
| 작성자, 관리자로 상세를 조회함 | `canDelete`가 `true` (#59) | 4-2 2절 | 통과 |
| 다른 동아리원으로 상세를 조회함 | `canDelete`가 `false` (#59) | 4-2 2절 | 통과 |

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

### 6-4 업로드

미작성. 권한 거부 케이스(비로그인 401, 승인 대기 회원 403)는 #40, #41에서 채운다.

<br>

### 6-5 앨범 삭제 (#59)

테스트 클래스는 `AlbumDeleteApiTest`(권한 거부), `AlbumDeleteIntegrationTest`(실제 DB), `AlbumCommandServiceTest`(처리 순서), `AlbumPermissionTest`(권한 규칙)다. S3는 대역으로 둔다.

| 케이스 | 기대 | 근거 | 통과 |
|---|---|---|---|
| 비로그인이 지우려 함 | 401 `UNAUTHORIZED`, 서비스까지 오지 않음 | 5-3-7 | 통과 |
| 승인 대기 회원이 지우려 함 | 403 `FORBIDDEN`, 서비스까지 오지 않음 | 5-3-7 | 통과 |
| 다른 동아리원이 지우려 함 | 403 `GALLERY_FORBIDDEN`, 앨범과 S3 객체가 남음 | USER-STORIES 3절 | 통과 |
| 운영진이 남의 앨범을 지우려 함 | 403 `GALLERY_FORBIDDEN` | 4-2 5절 | 통과 |
| 작성자가 지움 | 204, 앨범과 사진 행이 사라지고 S3 객체를 지움, 목록에서 바로 사라짐 | USER-STORIES 3절 | 통과 |
| 관리자가 남의 앨범을 지움 | 204, 앨범이 사라짐 | USER-STORIES 3절 | 통과 |
| 없는 앨범 | 404 `GALLERY_ALBUM_NOT_FOUND` | 5-3-7 | 통과 |
| 처리 순서 | DB 삭제와 flush 뒤에 S3 삭제 | 5-2-2 | 통과 |
| S3 삭제가 실패함 | 500 `INTERNAL_ERROR`, 앨범과 사진 행이 남음 | 5-2-2 | 통과 |
| 승인 대기로 내려간 작성자 | 지울 수 없음 (`canDelete`도 `false`) | 5-1 1절 | 통과 |

지운 뒤 10분 동안 CDN 캐시에서 사진이 열리고 그 뒤로 열리지 않는지는 CloudFront 설정이라 이 테스트로 확인하지 않는다. 인프라가 올라간 뒤 확인한다.
