# 7장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2, Tomcat 11.0.27, Jackson 3.1.7
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지, 격리 수준은 기본값 REPEATABLE READ)
- Docker 28.3.0

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch07-start`(6장 코드 그대로 — 잠금 없음, 준비는 찾아서 없으면 남김, 인증 상태 결제에 같은 키는 409, 주기 작업은 서버마다 돎)에서 7장 테스트를 돌린 결과
- `02-final-test-result.txt`: 태그 `ch07`에서 전체 테스트(`./gradlew clean check`)를 돌린 결과와 커버리지
- `03-comparison-known-failing-result.txt`: `./gradlew knownFailingTest`로 계약을 못 지키는 비교 구현을 돌린 결과(2~6장 비교 구현 포함)
- `04-concurrency-measurement.txt`: `./gradlew concurrencyMeasurement`로 잠금 방식마다 같은 결제에 동시 요청 N개를 보낸 결과와, 서버 수마다 보정 주기 한 번의 카드사 조회 횟수

### 겹침을 고정하는 방법

동시 요청 시험은 실행마다 같은 순서로 겹쳐야 한다. 겹치는 순간을 시간에 맡기지 않고 아래처럼 고정했다.

- 기록 붙잡기(`SlowWrites`): MySQL 트리거가 먼저 보낸 요청의 기록 한 건에서 `do sleep(1)`로 1초 잠든다(결제 상태를 바꾸는 UPDATE 는 BEFORE UPDATE,
  새 결제·새 취소 요청은 AFTER INSERT — 그 줄의 잠금을 쥔 채 잠든다). 시험은 `information_schema.processlist`에서 `User sleep` 상태를 본 뒤에
  두 번째 요청을 보낸다. 두 번째 요청은 앞 요청이 커밋하기 전에 결제를 읽는다. 2·4·5장에서 기록 실패를 트리거로 만든 것과 같은 방법이다.
- 카드사 쪽: Mock PG 의 승인 지연(0.8초, 카드사가 처리 중인 것을 `isWaiting`으로 확인한 뒤 다시 보냄)과 승인 답 붙잡기(6장).
- 서버 두 대(`OtherPaymentServer`): 같은 MySQL·Mock PG 를 쓰는 결제 서버를 시험 JVM 에 하나 더 띄운다(웹 없이, 주기 실행 끔).
  두 서버가 래치로 같은 때 주기 작업을 시작하고, 카드사는 조회·취소에 0.5초 늦게 답해 한 서버가 끝나기 전에 다른 서버도 시작한다.
  다른 서버를 띄울 때 설정 값은 실행 인자로 넘긴다 — `SpringApplicationBuilder.properties`(기본 속성)는 `application.properties`보다 약해
  처음에 PG 주소가 8090 으로 남았다.

반복 실행으로 흔들리지 않는지 확인했다(구현 보고에 횟수와 결과).

## Mock PG

7장에서 더한 것: 주문번호 결제 조회를 받은 횟수(`inquiryCount`, 따로 띄우면 `GET /control/inquiries/{orderId}`). 답을 늦추거나 실패로 답한 조회도 센다.

## 비교 구현을 끼우는 방법

- 결정 1(잠금 방식): 운영 서비스(승인·취소·알림 반영·보정)를 그대로 쓰고, 결제를 바꾸려고 읽는 저장소(`findByOrderIdForUpdate`)만 우선 빈으로 바꾼다.
  변경 번호 비교는 결제에 `@Version` 을 두지 않고 시험 DB 에만 `version` 칸을 더해, 커밋 직전 `update … where version = 읽은 번호`로 흉내 냈다
  (취소 줄만 더하는 기록에도 번호를 올린다 — JPA `OPTIMISTIC_FORCE_INCREMENT` 와 같다). 상태 조건 UPDATE 도 커밋 직전 `update … where status = 읽은 상태`.
  어긋나면 409(INVALID_PAYMENT_TRANSITION)로 답하고 다시 시도하지 않는다.
- 결정 5(서버끼리 나누기): 결제마다 점유 비교는 시험 DB 의 payment·payment_webhook 에만 `claimed_by`, `claimed_until` 을 더한다.

## 부하 테스트

7장에는 처리량에 대한 주장이 없어 k6 를 돌리지 않았다. 동시 요청의 응답 시간·답은 `04-concurrency-measurement.txt`에 있다.
