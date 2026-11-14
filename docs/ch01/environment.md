# 1장 실행 환경

- 머신: Apple M1 Max, 메모리 32GB, macOS (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, Lombok, JaCoCo 0.8.13
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4.11 (`mysql:8.4` 이미지)
- Docker 28.3.0
- Spring Statemachine 4.0.2 (비교 구현용, 테스트 의존성. 2026-07-05 GitHub 저장소 보관)

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch01-start`에서 전이 규칙 테스트를 돌린 결과
- `02-final-test-result.txt`: 태그 `ch01`에서 전체 테스트를 돌린 결과
