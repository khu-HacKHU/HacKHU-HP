# backend

HacKHU 홈페이지 API 서버. Spring Boot 4.1 / Java 21 / PostgreSQL 17.

작업 규칙은 [CLAUDE.md](CLAUDE.md)에 있다.

## 준비물

- **JDK**: 따로 설치하지 않아도 된다. 처음 빌드할 때 Gradle이 JDK 21을 자동으로 내려받는다
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

값은 GitHub Secrets 또는 AWS 파라미터 스토어에만 둔다.
