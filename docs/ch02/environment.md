# 2장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2, Tomcat 11.0.27, Jackson 3.1.7
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지)
- Docker 28.3.0 (Docker에 할당된 CPU 10개, 메모리 약 15.6GiB)
- k6 v1.7.1

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch02-start`(트랜잭션 하나로 묶은 승인)에서 2장 테스트를 돌린 결과
- `02-final-test-result.txt`: 태그 `ch02`에서 전체 테스트(`./gradlew check`)를 돌린 결과와 커버리지
- `03-comparison-known-failing-result.txt`: 태그 `ch02`에서 `./gradlew knownFailingTest`로 계약을 못 지키는 비교 구현을 돌린 결과

계약 테스트(`PaymentConfirmContract`)와 승인 대기 중 조회 테스트(`PendingApprovalApiTest`)는 DB 연결을 2개, 연결 대기 시간을 250ms(Hikari 최솟값)로 줄여 돌린다.

## 부하 테스트 (k6)

실행: `load-test/ch02/run.sh <결제 서버 jar> <Mock PG jar> <결과 이름>`

- 결제 서버, Mock PG, MySQL(Docker), k6 를 모두 같은 머신에서 띄운다. 회차마다 MySQL 컨테이너를 새로 만든다.
- 결제 서버 설정은 기본값 그대로다: Hikari 연결 10개(DEFAULT_POOL_SIZE), 연결 대기 30초, Tomcat 기본 스레드.
- Mock PG 는 모든 승인 요청에 1초 뒤 승인으로 답한다.
- 시나리오(`lookup-while-confirming.js`)
  - 승인: 20 VU 가 쉬지 않고 결제 준비 → 승인을 반복(constant-vus)
  - 조회: 초당 20건, 미리 준비한 결제 하나를 조회(constant-arrival-rate)
  - 10초 예열(결과 버림) 뒤 30초 측정
- 결과: `load-test/ch02/results/<이름>-k6.txt`, `<이름>-summary.json`
  - `ch02-start`, `ch02-start-run2`: 태그 `ch02-start` 빌드
  - `ch02`, `ch02-run2`: 태그 `ch02` 빌드
