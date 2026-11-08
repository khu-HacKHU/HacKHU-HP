# backend

HacKHU 홈페이지 API 서버. Spring Boot 4.1 / Java 21 / PostgreSQL 17.

작업 규칙은 [AGENTS.md](AGENTS.md)에 있다.

## 준비물

- **JDK 17 이상**: Gradle을 실행하는 데 필요하다. 컴파일용 JDK 21은 처음 빌드할 때 Gradle이 자동으로 내려받는다
- **Docker Desktop**: 로컬 DB와 통합 테스트에 필요하다

## 실행

```bash
cd backend
./gradlew bootRun          # Windows: gradlew.bat bootRun
```

- `compose.yaml`의 PostgreSQL이 자동으로 뜨고 연결된다 (`local` 프로파일)
- 헬스체크: http://localhost:8080/actuator/health
- API 문서 (Swagger UI): http://localhost:8080/swagger-ui.html

## 빌드·테스트

```bash
./gradlew spotlessApply    # 포매팅 자동 수정
./gradlew build            # 포매팅 검사 + 컴파일 + 테스트
```

CI도 `./gradlew build`를 그대로 돌린다. 로컬에서 통과하면 CI도 통과한다.

## 환경변수 (`prod` 프로파일)

| 변수 | 설명 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:postgresql://호스트:5432/DB이름` |
| `DATABASE_USERNAME` | DB 계정 |
| `DATABASE_PASSWORD` | DB 비밀번호 |
| `FRONTEND_ORIGIN` | CORS로 허용할 프론트엔드 주소 하나 (예: `https://hackhu.example`). `*`는 받지 않는다 |
| `GALLERY_CDN_BASE_URL` | 갤러리 사진 CloudFront 배포 주소 (`https://...`) |
| `GALLERY_CDN_KEY_PAIR_ID` | CloudFront 공개 키 ID |
| `GALLERY_CDN_PRIVATE_KEY` | CloudFront 서명용 RSA 개인 키 (PKCS#8 PEM) |

`GALLERY_CDN_*`가 하나라도 없으면 서버가 뜨지 않는다. `local`에서는 없어도 임시 키로 뜬다 (사진 주소는 실제로 열리지 않음).

값은 GitHub Secrets 또는 AWS 파라미터 스토어에만 둔다.