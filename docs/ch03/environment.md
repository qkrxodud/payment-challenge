# 3장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2, Tomcat 11.0.27, Jackson 3.1.7
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지)
- Docker 28.3.0

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch03-start`(받은 금액을 그대로 PG에 보내는 승인)에서 3장 테스트(승인 요청 확인 계약, 결제 API)를 돌린 결과
- `02-final-test-result.txt`: 태그 `ch03`에서 전체 테스트(`./gradlew check`)를 돌린 결과와 커버리지
- `03-comparison-known-failing-result.txt`: 태그 `ch03`에서 `./gradlew knownFailingTest`로 계약을 못 지키는 비교 구현을 돌린 결과(2장 비교 구현 포함)

승인 요청 확인 계약(`ConfirmRequestCheckContract`)의 준비 금액은 15000 이고, DB 컬럼이 `decimal(19, 2)`라 읽으면 `15000.00`이 된다. 요청 금액은 `new BigDecimal("15000")`(소수 자릿수 0)으로 보낸다.

Mock PG는 받은 금액을 그대로 승인하고 주문별로 기록한다. 고객이 결제창에서 인증한 금액이 곧 승인 요청의 금액이라고 가정한 것이다.

## 부하 테스트

3장에는 처리량·지연·경합에 대한 주장이 없어 k6 를 돌리지 않았다.
