# backend 규칙

루트 [AGENTS.md](../AGENTS.md)의 전역 규칙과 [시큐어 코딩 규칙](../docs/security/secure-coding.md)을 먼저 따른다. 여기는 백엔드에만 해당하는 규칙이다.

## 스택

Spring Boot 4.1 / Java 21 / Gradle Kotlin DSL / PostgreSQL 17 / Flyway / Spring Data JPA / springdoc-openapi

## 패키지 구조

```
com.hackhu.hp
  domain/                    기능 단위. 팀별로 나뉜다
    auth/                    인증·권한 팀 — 로그인, 토큰 발급·갱신, OAuth2
    user/                    인증·권한 팀 — 회원, 역할, 가입 승인
    notice/  post/           게시판 팀
    gallery/                 갤러리 + 인프라 팀
      controller/ service/ repository/ entity/ dto/ exception/
  global/                    도메인에 속하지 않는 공통 코드
    error/                   공통 에러 응답 (이미 있음)
    security/                SecurityConfig, 필터 — 인증·권한 팀
    auth/                    토큰 파싱, 현재 사용자 조회 — 인증·권한 팀
    storage/                 S3 presigned URL — 갤러리 + 인프라 팀
    config/                  그 외 설정
```

- `global/security`, `global/auth`, `global/storage`, `global/error`, `domain/auth`는 PL이 코드 오너다 ([CODEOWNERS](../.github/CODEOWNERS)).
- 다른 팀의 `domain/*` 내부 클래스를 직접 부르지 않는다. 필요하면 그 팀의 service에 공개 메서드를 요청한다.

## 코드 규칙

- 컨트롤러 경로는 `/api/v1`로 시작한다 ([API 규약](../docs/conventions/api.md)). 예외: `/actuator/health`, `/v3/api-docs`, `/swagger-ui`
- 컨트롤러는 엔티티를 반환하지 않는다. 요청·응답은 `record` DTO로 만든다
- 요청 DTO에 `role`, `status`, `authorId`처럼 사용자가 정하면 안 되는 필드를 두지 않는다 (Mass Assignment)
- 현재 사용자 ID는 요청 본문이 아니라 인증 정보에서 꺼낸다
- 엔티티에 `@Setter`와 public 기본 생성자를 두지 않는다. 생성은 정적 팩토리, 변경은 의도가 드러나는 메서드(`post.edit(title, content)`)로 한다
- 입력 검증은 DTO에 Bean Validation(`@NotBlank`, `@Size` 등)으로 하고 컨트롤러에서 `@Valid`를 붙인다
- 실패는 `BusinessException(ErrorCode)`으로 던진다. 도메인 에러 코드는 `domain/{도메인}/exception/{도메인}ErrorCode` enum으로 만들고 `ErrorCode`를 구현한다
- SQL은 JPA·Spring Data 메서드 또는 파라미터 바인딩(`@Query` + `:param`)만 쓴다. 문자열 연결로 JPQL·SQL을 만들지 않는다
- 정렬·검색 필드는 허용 목록으로 검사한다
- 목록 API는 `Pageable`을 받고 `size` 최대 100으로 제한한다

## DB

- 스키마 변경은 Flyway 마이그레이션(`src/main/resources/db/migration/V{번호}__{설명}.sql`)으로만 한다. `ddl-auto`는 `validate`로 고정한다
- 머지된 마이그레이션 파일은 고치지 않는다. 바꿔야 하면 새 버전을 추가한다
- 마이그레이션 번호가 다른 PR과 겹치면 늦게 머지하는 쪽이 번호를 올린다

### 시각 컬럼

- **시점을 담는 컬럼은 `TIMESTAMPTZ`로 만든다.** `TIMESTAMP`는 쓰지 않는다. 날짜만 필요하면(활동 날짜, 생일) `DATE`를 쓴다
- **엔티티는 `Instant`로 매핑한다.** 오프셋을 그대로 다뤄야 하면 `OffsetDateTime`도 된다. `LocalDateTime`은 쓰지 않는다
- 비교와 계산은 UTC로 한다. KST로 바꾸는 것은 화면뿐이다 ([API 규약](../docs/conventions/api.md) 2절)

`TIMESTAMP`는 시계에 적힌 값을, `TIMESTAMPTZ`는 시점을 저장한다. `created_at`이나 `expires_at`이 뜻하는 것은 시점이다. `TIMESTAMP`를 쓰면 그 값이 어느 시점인지 알아내는 정보를 버리고 코드 쪽 약속으로만 보관하게 된다. 그러면 세 가지가 따라온다.

- `DEFAULT now()`가 **접속한 세션의 시간대**로 잘려 저장된다. UTC 세션과 KST 세션이 같은 문장으로 다른 값을 넣고, 어느 쪽이었는지 스키마에 남지 않는다
- 도메인을 넘는 시각 비교에 캐스팅이 끼고, 그 캐스팅도 세션 시간대를 쓴다
- 토큰 만료·폐기 판정(`expires_at`, `revoked_at`)이 DB 세션 설정에 걸린다

Postgres에서 둘 다 8바이트라 `TIMESTAMPTZ`가 더 크거나 느리지 않다.

엔티티 타입을 함께 정하는 이유가 있다. 컬럼이 `TIMESTAMPTZ`여도 엔티티가 `LocalDateTime`이면 Hibernate가 변환에 시간대를 써서 같은 문제가 자바 쪽으로 옮겨 간다. `Instant`는 그 변환이 없고, Jackson이 API 규약이 요구하는 `"2026-10-12T09:30:00Z"` 형식으로 바로 직렬화한다. 안전망으로 `application.yml`에 `hibernate.jdbc.time_zone: UTC`도 둔다.

## 설정과 시크릿

- 프로파일: `local`(기본, docker compose로 DB 자동 실행), `prod`(환경변수로 주입)
- `application*.yml`에 비밀번호·키를 적지 않는다. `${ENV_VAR}`로만 참조한다
- API 문서(Swagger UI, `/v3/api-docs`)는 기본으로 꺼져 있고 `local`에서만 켠다. 다른 프로파일에서 켜지 않는다

## 테스트

- 권한이 걸린 API는 **거부 케이스를 반드시 테스트한다** — 비로그인 401, 다른 사용자 403(또는 404)
- 컨트롤러 단위는 `@WebMvcTest`, DB가 필요하면 `@SpringBootTest` + `TestcontainersConfiguration`을 쓴다 (Docker 필요)
- 포매팅이 어긋나면 `./gradlew build`가 실패한다. 커밋 전에 `./gradlew spotlessApply`를 돌린다
