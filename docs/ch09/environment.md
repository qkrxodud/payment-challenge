# 9장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2(연결 10개, 기본값), Tomcat 11.0.27, Jackson 3.1.7 — 8장과 같다
- 9장에서 더한 것: Spring Kafka 4.1.2, kafka-clients 4.2.2(`spring-boot-starter-kafka`), 브로커 `apache/kafka:4.2.0` 한 대(KRaft, 브로커·컨트롤러 겸함, 복제본 1)
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지, 격리 수준은 기본값 REPEATABLE READ)
- Docker 28.3.0 (Docker 에 잡힌 메모리 15.6GiB, 시험과 상관없는 다른 컨테이너도 함께 실행 중이었다)

## 모듈과 DB

- 루트: 결제 서버. `mock-pg`: 가짜 PG(8장과 같음).
- `order-service`(9장): 결제 이벤트 토픽(`payment-events`, 파티션 2개)을 읽어 받은 이벤트를 그대로 남기고(`received_payment_event`, 같은 이벤트가 다시 오면 한 줄 더),
  주문 상태(`orders`)를 마지막으로 받은 결제 결과대로 바꾼다. 웹 API 는 없다. 중복을 가리거나 늦게 온 옛 이벤트를 거르지 않는다(11장).
  파티션마다 따로 읽는다(`spring.kafka.listener.concurrency=2`). 처리에 실패하면 Spring Kafka 기본 처리(바로 다시 시도, 10번째에 로그를 남기고 넘어감)를 그대로 둔다.
- 주문 서비스의 DB 는 결제 서버와 **같은 MySQL 컨테이너의 다른 스키마**(`order_service`)를 **다른 계정**(`order_service`, 그 스키마에만 권한)으로 쓴다.
  컨테이너를 하나 더 띄우지 않으려고(8장 관찰 1 — 시험 컨텍스트마다 MySQL 컨테이너가 하나씩 뜬다) 같은 컨테이너에 두었고, 계정을 나눠 결제 서버와 주문 서비스가
  서로의 표를 읽지 못하게 했다. 스키마와 계정은 시험이 MySQL 관리자 계정으로 만든다(`OrderServer`).

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch09-start`(상태를 바꾼 그 자리에서 키 없이 바로 보내는 출발 코드)에서 `./gradlew clean test`를 돌려 9장 테스트
  (`PaymentEventContract` 7개)만 뽑은 결과. 출발 구조 뒤에 더한 9장 테스트(`PaymentOutboxTest`, `PaymentOutboxRelayTest`, `OutboxRelayContract`)는 남긴 결과 표와
  릴레이가 있어야 해서 출발 코드에 얹지 않았다. 같은 실행에서 앞 장 648개는 모두 통과했다
- `02-final-test-result.txt`: 태그 `ch09`에서 전체 테스트(`./gradlew clean check`)와 커버리지. 주문 서비스 모듈의 테스트(도메인 단위 시험)는 맨 끝에 따로 붙였다
  (주문 서비스 모듈에는 커버리지 검사를 걸지 않았다 — mock-pg 와 같다)
- `03-comparison-known-failing-result.txt`: `./gradlew knownFailingTest`(2~9장 비교 구현). 뒤이어 도는 7장 `jpaVersionTest` 결과를 맨 끝에 붙였다
- `04-outbox-measurement.txt`: `./gradlew outboxMeasurement`

### Kafka 브로커와 중계(BrokerLink)

- 시험 JVM 에 브로커를 하나만 띄워(`KafkaBroker`) 모든 시험 컨텍스트가 같이 쓴다. 토픽 `payment-events`(파티션 2개)는 브로커를 띄우며 만들고, 브로커의
  토픽 자동 생성은 끈다 — 처음에는 결제 서버보다 먼저 뜬 주문 서비스가 구독하면서 브로커가 파티션 하나짜리 토픽을 만들어, 출발 구조의 순서 계약이 통과했다(실행해 확인).
- 결제 서버·주문 서비스는 모두 시험 JVM 안의 TCP 중계를 거쳐 브로커에 닿는다. 브로커가 알려 주는 주소(advertised listener)도 중계의 포트다.
  - 끊기(`cut`): 열린 연결을 모두 닫고, 되돌릴 때까지 새 연결도 받자마자 닫는다. 요청이 브로커에 닿지 않는다.
  - 늦추기(`slowDown`): 브로커가 보낸 답을 정한 시간만큼 붙잡았다가 넘긴다.
  - 컨테이너 멈춤(`docker pause`)은 쓰지 않았다: 멈춘 브로커도 운영체제가 요청 바이트를 받아 두었다가 다시 살아나면 처리할 수 있어서,
    보낸 쪽이 실패로 안 이벤트가 나중에 도착할 수 있다고 보았다(이 동작은 실행해 보지 않았다 — 가정).
  - 확인한 것(확인용 시험, 커밋하지 않음): 중계를 끊고 보내면 3009ms 뒤 `TimeoutException`으로 실패하고, 되돌린 뒤 토픽을 처음부터 읽어도 그 레코드는 없었다.
    답을 1.5초 붙잡으면 보내기가 1511ms 걸렸다.
- 시험의 프로듀서 시간 제한: `delivery.timeout.ms=3000`, `request.timeout.ms=2000`, `max.block.ms=3000`(운영 설정은 10초·5초·10초, Kafka 기본은 2분·30초·1분).
  브로커가 3초보다 오래 끊기면 보내기가 실패로 끝난다 — 운영에서 그보다 오래 끊긴 것과 같은 결과를 3초 만에 본다.
- 주문 서비스의 소비자 그룹은 띄울 때마다 새로 정하고 토픽의 처음부터 읽는다(시험 컨텍스트마다 주문 서비스가 따로 떠, 같은 그룹이면 파티션을 나눠 갖는다).

### 겹침·실패 시점을 고정하는 방법

- 순서(`PaymentEventContract`): 주문 서비스가 승인 이벤트를 받아 남기는 기록(`received_payment_event` insert)을 트리거로 1초 붙잡는다. 그 소비자가
  잠든 것을 processlist 의 `User sleep`(주문 서비스 계정의 연결)으로 확인한 뒤 결제를 전액 취소한다. 같은 파티션이면 취소 이벤트는 승인 처리가 끝날 때까지 기다리고,
  다른 파티션이면 다른 소비자가 먼저 처리한다. 받은 순서는 `received_payment_event`의 번호 순이다(MySQL 은 BEFORE INSERT 트리거가 끝난 뒤 자동 번호를 매긴다 —
  잠든 승인 기록이 나중 번호를 받은 것을 실행으로 확인).
- 키 없는 구현의 시험(출발 구조 `PaymentEventTest`(결정 3까지), `InTransactionEventTest`, `AfterCommitEventTest`, `UnkeyedEventTest`)은 돌아가며 고르는 파티셔너
  (`RoundRobinPartitioner`)를 쓴다. Kafka 기본 파티셔너는 키가 없으면 batch.size(16KB)를 채울 때까지 한 파티션에 붙어 보내서(아래 문서 인용), 이벤트 몇 건으로는
  한 파티션에만 간다 — 확인용 시험에서 300ms 간격 4건이 세 번 모두 한 파티션([0,0,0,0], [0,0,0,0], [1,1,1,1]), `RoundRobinPartitioner`는 세 번 모두 [1,0,1,0].
  많은 양에서 파티션이 바뀌는 것을 이벤트 두 건으로 보려는 것이다. 이 파티셔너는 키를 보지 않아 키를 쓰는 운영(결정 4부터)에는 쓰지 않는다.
  측정(`04`)의 키 없는 방식은 기본 파티셔너를 그대로 쓴다.
- 유실: 결제 직전에 중계를 끊고, 끊은 때부터 4초(보내기 시간 제한 3초 + 1초) 뒤에 되돌린 다음 15초 안에 도착하는지 본다.
- 유령: 2장과 같이 결제를 승인 상태로 바꾸는 UPDATE 를 트리거로 막는다(커밋 때 실패). 그 뒤 승인한 다른 결제의 승인이 도착하고 1초 더 기다린 동안 앞 결제의 승인이 오지 않는지 본다.
- 응답 지연: 브로커 답을 1.5초 붙잡은 채 결제 하나를 승인하고 걸린 시간을 잰다(컨텍스트 뒤 첫 승인은 늦을 수 있어 한 번 승인해 둔 뒤).
- 서버 두 대(`OutboxRelayContract`): 7장 `OtherPaymentServer`로 결제 서버를 한 대 더 띄우고, 두 서버가 래치로 같은 때 릴레이 주기를 시작한다.
  승인 이벤트의 첫 보내기 한 번만 1초 붙잡는다(`SendFaults` — 두 서버가 같이 쓰는 정적 상태라 어느 서버든 먼저 보내는 쪽이 붙잡힌다).
- 보내지 못함·죽음: `SendFaults`가 정한 결과의 첫 보내기를 실패시키거나(보내지 못함), 그 자리에서 릴레이를 죽인다(먼저 남기는 비교). 보낸 뒤 남기는 구현이
  남기기 전에 죽는 것은 `payment_outbox` UPDATE 를 트리거로 막아 만든다.
- 릴레이 주기 실행은 기본 시험에서 끈다(`payment.outbox.enabled=false`, build.gradle). `PaymentEventContract`만 켜고 0.1초마다 돌리며, 릴레이 계약은 주기를 직접 부른다.

반복 실행으로 흔들리지 않는지 확인했다: 9장 기본 테스트 23개(PaymentEventTest·OutboxRelayTest·PaymentOutboxRelayTest·PaymentOutboxTest) 5번 모두 통과,
9장 비교 45개 3번 모두 같은 19개 실패(실패 이름 목록이 같음).

## Kafka·Spring 문서에서 확인한 것과 가정

- Kafka 4.2 프로듀서 설정(https://kafka.apache.org/42/configuration/producer-configs/):
  - `acks` 기본 `all` — "This means the leader will wait for the full set of in-sync replicas to acknowledge the record."
  - `enable.idempotence` 기본 `true` — "When set to 'true', the producer will ensure that exactly one copy of each message is written in the stream."
    / "Idempotence is enabled by default if no conflicting configurations are set."
  - `delivery.timeout.ms` 기본 120000 — "An upper bound on the time to report success or failure after a call to `send()` returns."
    / "The value of this config should be greater than or equal to the sum of `request.timeout.ms` and `linger.ms`."
  - `partitioner.class` — "If no partition is specified but a key is present, choose a partition based on a hash of the key."
    / "If no partition or key is present, choose the sticky partition that changes when at least batch.size bytes are produced to the partition."
  - `max.in.flight.requests.per.connection` — "if retries are disabled or if `enable.idempotence` is set to true, ordering will be preserved."
- Spring Framework 트랜잭션 이벤트(https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html):
  "The valid phases are `BEFORE_COMMIT`, `AFTER_COMMIT` (default), `AFTER_ROLLBACK`, as well as `AFTER_COMPLETION` …".
  커밋 뒤 리스너가 같은 스레드에서 돈다는 문장은 이 문서에서 찾지 못했다 — 비교 구현에서 커밋 뒤 보내기가 결제 승인 응답을 3.29초로 늘린 것(실행)으로만 안다.
- 가정(확인 못 함): Kafka 트랜잭션과 DB 트랜잭션을 함께 묶는 선택지, MySQL binlog CDC(Debezium)는 구현하지 않았다(결정 1·2의 구현하지 않은 선택지).

## 비교 구현을 끼우는 방법

- 결정 1(언제 보내나): 운영 `PaymentEvents`(남기기)를 `@Primary` 하위 클래스로 바꾼다 — 그 자리에서 바로 보냄(`InTransactionPaymentEvents`), 커밋 뒤 보냄
  (`AfterCommitPaymentEvents`, `@TransactionalEventListener(AFTER_COMMIT)`). 둘 다 남겨 두는 것이 없다.
- 결정 3(서버끼리 나누기): 시험이 릴레이 주기를 부르는 방법만 바꾼다 — 작업 잠금 없이 운영 릴레이(잠그지 않음), `SkipLockedOutboxRelay`(한 줄씩
  `select … for update skip locked`로 맡아 보내고 커밋).
- 결정 4(키): 보내기를 키 없는 `UnkeyedEventSender`로 바꾼다.
- 결정 5(차례): `RecordFirstOutboxRelay`(보낸 것으로 먼저 남기고 보냄)를 작업 잠금 안에서 부른다.

## 부하 테스트

k6 는 돌리지 않았다. 방식마다의 유실·중복·순서·지연과 릴레이 처리량은 시험 JVM 안에서 잰 `04-outbox-measurement.txt`에 있다.
