# 3-1 소프트웨어 아키텍처

본 문서는 갤러리 백엔드의 구성 요소, 패키지 구조와 의존 방향을 정한다. 공통 패키지 규칙은 레포의 백엔드 규칙을 따른다. ([backend/AGENTS.md](../../../../backend/AGENTS.md))

<br>

### 3-1-1 패키지

| 패키지 | 두는 것 |
|---|---|
| `domain/gallery/controller` | `AlbumController` |
| `domain/gallery/service` | `AlbumQueryService`, `PhotoUrlResolver`, `CloudFrontPhotoUrlResolver` |
| `domain/gallery/repository` | `AlbumRepository`, `PhotoRepository` |
| `domain/gallery/entity` | `Album`, `Photo` |
| `domain/gallery/dto` | 요청과 응답 `record` |
| `domain/gallery/exception` | `GalleryErrorCode` |
| `domain/gallery/config` | 조회 주소 설정(`GalleryCdnProperties`)과 빈 등록 |

의존 방향은 controller에서 service, service에서 repository 순이다. 컨트롤러는 엔티티를 반환하지 않는다.

<br>

### 3-1-2 조회 주소

사진 조회 주소는 `PhotoUrlResolver`가 만든다. 조회 API는 이 인터페이스만 알고 서명 방식은 모른다.

| 항목 | 값 |
|---|---|
| 구현 | `CloudFrontPhotoUrlResolver`. CloudFront canned policy 서명 URL |
| 만료 | 1시간. 조회할 때마다 새로 서명함 (2-REQUIREMENTS 3절) |
| 외부 호출 | 없음. 개인 키로 서버 안에서 서명함 |
| 라이브러리 | AWS SDK for Java v2 `cloudfront` 모듈. 버전은 `s3` 모듈과 같게 맞춤 |

설정은 환경변수로만 받는다.

| 환경변수 | 내용 |
|---|---|
| `GALLERY_CDN_BASE_URL` | CloudFront 배포 주소. https만 받음 |
| `GALLERY_CDN_KEY_PAIR_ID` | CloudFront 공개 키 ID |
| `GALLERY_CDN_PRIVATE_KEY` | 서명용 RSA 개인 키, PKCS#8 PEM. 시크릿 |

- 셋 중 하나라도 비어 있으면 서버가 뜨지 않는다. 열리지 않는 사진 주소를 내려주는 대신 배포 단계에서 바로 드러나게 한다.
- `local` 프로파일은 개인 키가 없으면 서버가 시작될 때 임시 키를 만든다. 이 키로 만든 주소는 실제로 열리지 않는다.
- 개인 키의 보관 위치와 주입 경로는 미정이다. 배포 문서에서 정한다. ([DEPLOYMENT.md](../../DEPLOYMENT.md))

<br>

### 3-1-3 업로드 주소 발급

미작성. presigned PUT 발급과 만료 처리는 #40에서 정한다.
