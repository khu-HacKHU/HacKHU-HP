# 3-2 DB 스키마

본 문서는 갤러리 기능이 정하는 테이블만 다룬다. 사용자 테이블은 인증권한팀 소유이며 FK로만 참조한다 ([auth](../../auth/BACKGROUND.md)).

<br>

### 1. album

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| title | VARCHAR(100) | NOT NULL | |
| event_date | DATE | NOT NULL | 활동 날짜. 목록 정렬 기준 |
| uploader_id | BIGINT | NOT NULL, FK → `user.id` | 올린 사람. 탈퇴해도 행은 남김 (USER-STORIES) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | |

인덱스: `(event_date DESC)`. 목록이 항상 이 순서로 조회된다.

**확정**: `user` 테이블이 아직 마이그레이션되지 않아서(인증권한팀 진행 중), 지금은 FK 없이 `uploader_id`만 둔다. 인증권한팀 마이그레이션이 끝난 뒤 FK만 추가하는 마이그레이션을 별도로 낸다. 번호는 그때 순서 맞춰서 정한다.

<br>

### 2. photo

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| album_id | BIGINT | NOT NULL, FK → `album.id` ON DELETE CASCADE | |
| object_key | VARCHAR(255) | NOT NULL, UNIQUE | S3 객체 키. 형식 `{용도}/{uuid}.{확장자}`, 확장자는 매직바이트 검증 후 확정된 실제 형식 기준 |
| display_order | SMALLINT | NOT NULL | 0부터 시작. 0번이 대표 사진 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | |

제약: `UNIQUE (album_id, display_order)`. 같은 앨범 안에서 순서 중복을 방지한다.

**1~20장 개수 제한은 DB 제약이 아니라 애플리케이션 레벨에서만 검사한다.** 쓰기 경로가 앨범 생성 API 하나뿐이라(수정 API 없음, D9) 트리거로 이중 방어할 이유가 없다고 보고 **확정**.

<br>

### 3. 데이터 수명

- 앨범 삭제 → `photo` 행은 DB 레벨에서 CASCADE로 같이 삭제된다.
- S3의 실제 객체 삭제는 **DB 트랜잭션과 같은 요청 안에서 동기로 지운다.** USER-STORIES의 "삭제 후 10분 그레이스"는 CDN 캐시 TTL로 구현하므로 별도 비동기 삭제 큐가 필요 없다 (5-1 참고).

<br>

### 4. 참고: 업로드 발급 기록 테이블 (이 도메인 소유 아님)

`uploadId + DB 발급기록` 방식의 실제 테이블은 갤러리가 아니라 `global/storage`(PL 소유, 게시판 첨부파일과 공유)에 둔다. 여기엔 정의하지 않고 참조만 한다. 컬럼 구성은 `id(uploadId)`, `user_id`, `purpose`, `object_key`, `content_type`, `size_bytes`, `status(ISSUED/USED)`, `expires_at`, `created_at`.

확정 없이 끝까지 `ISSUED` 상태로 남는 행이 쌓이면 S3에 쓰레기 객체도 같이 쌓인다. 만료된 `ISSUED` 기록을 찾아 DB 행과 S3 객체를 같이 지우는 배치가 필요하다 (5-1 참고). 이 배치도 `global/storage` 소유라 여기서 구현하지 않고, 인프라팀 쪽에 필요하다고 전달만 한다.
