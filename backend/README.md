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
| `JWT_SECRET` | access 토큰 서명 키. 32바이트 이상의 무작위 값을 Base64로 인코딩한 값 (예: `openssl rand -base64 32`의 출력). 없거나 짧으면 서버가 시작하지 않는다. 바꾸면 기존 access 토큰은 401이고 프론트엔드가 refresh로 이어 간다 |
| `TRUSTED_PROXY_CIDRS` | 로드밸런서 서브넷의 CIDR. 쉼표로 여러 개. 이 대역에서 온 연결만 `X-Forwarded-For`를 보고 클라이언트 IP를 정한다. 로드밸런서의 `X-Forwarded-For` 처리는 기본값(append)이어야 한다 |
| `TRUSTED_PROXY_HOPS` | 믿는 프록시 수. 기본 1(로드밸런서만 있을 때) |

`local` 프로파일은 `JWT_SECRET` 없이도 실행된다. 실행할 때마다 무작위 서명 키를 만든다.

값은 GitHub Secrets 또는 AWS 파라미터 스토어에만 둔다.
