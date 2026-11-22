# 11장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2(연결 10개, 기본값), Tomcat 11.0.27, Jackson 3.1.7,
  Spring Kafka 4.1.2, kafka-clients 4.2.2, 브로커 `apache/kafka:4.2.0` 한 대 — 10장과 같다
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지, 격리 수준은 기본값 REPEATABLE READ)
- Docker 28.3.0 (시험과 상관없는 다른 컨테이너도 함께 실행 중이었다)
- 출발점: 원격 main = chapter-10 = d5bfd50(태그 ch10 뒤 README 커밋 하나)
- 긴 실행은 `caffeinate -dims` 로 돌렸다. 처음 실행 하나는 머신이 잠들어(로그에 Hikari "clock leap 8m57s") 시험 몇 개가 수백 초 걸려 버렸다.

## 모듈과 DB

- 루트: 결제 서버, `mock-pg`: 가짜 PG — 10장과 같다.
- `order-service`(11장에서 바뀜): 결제 이벤트를 받는 소비자.
  - 결정 1: `received_payment_event.event_id` 고유 키, `insert ignore` 한 문장으로 받은 번호를 남기고 남겨졌을 때만 주문을 바꾼다(같은 트랜잭션). 받은 기록은 JPA 엔티티에서 뺐다.
  - 결정 2: `orders.last_event_id`(마지막으로 반영한 이벤트 번호). 주문 줄은 `select … for update`.
  - 결정 3: `DefaultErrorHandler` 빈(1·2·4·8초, 영구 오류는 다시 시도하지 않음). 결정 4: `payment-events.DLT`(실패 모음, 파티션 2), 다시 넣기 명령.
  - 업무 예외는 `domain/error`(ErrorCode, OrderServiceException, 예외 둘). 웹 진입점이 없어 전역 예외 처리기는 없다(승인된 규칙 예외).
- `inventory-service`(11장에서 바뀜): 재고 명령을 받는 소비자. 결정 1: `processed_stock_command.command_id` 고유, `StockCommandService` 가 남긴 뒤 `ReservationRecorder` 로
  확정·해제. 결정 2: `stock_reservation.last_command_id`(HTTP 로 잡은 예약은 비어 있음). 결정 3: 확정·해제가 아닌 명령은 영구 오류(10장은 확정이 아니면 해제했다).
  결정 4: `stock-reservation-commands.DLT`, 다시 넣기 명령.
- 메시지 번호: 결제 이벤트 `eventId`, 재고 명령 `commandId` 모두 결제 서버 `payment_outbox.id`(auto_increment)다.
  같은 결제의 기록은 결제 줄을 `select … for update` 로 잠근 트랜잭션(7장 결정 1)에서 남겨 번호가 만든 순서대로 커진다.
- 이미 만든 DB(9·10장 스키마)에는 `create table if not exists` 가 새 칸·키를 더하지 않는다. 시험은 컨텍스트마다 새 스키마를 만든다. 옮길 때는 칸을 더하고,
  `received_payment_event` 의 같은 번호 줄을 지운 뒤 고유 키를 더해야 한다(옮기는 스크립트는 만들지 않았다).

## 설정 값

- 다시 시도(결정 3, `order.retry.*`·`inventory.retry.*`): 첫 간격 1초, 2배씩, 최대 간격 8초, 4번 → 1·2·4·8초(기다림 합 15초).
  - 근거(실행): 이 머신에서 MySQL 8.4 컨테이너를 `docker restart` 해 다시 쿼리를 받기까지 2.62·2.69·2.62·2.62·3.04초(5번, `docker exec … mysql -e 'select 1'` 이
    성공할 때까지 0.1초마다 봄 — 시험 밖에서 잰 값). 15초는 그 약 5배다.
  - 가정: 5배라는 여유. 잰 것은 이 머신의 컨테이너 재시작 시간뿐이고, 15초를 고른 것은 그 위에 얹은 가정이다. 운영 DB 전환 시간 기준은 따로 없다(사용자 답, 2026-11-22).
  - 한 번 기다림의 최대(8초)는 `max.poll.interval.ms` 기본값(5분)보다 훨씬 짧다. Spring Kafka 문서: 기본 처리기는 기다리는 동안 소비자 스레드를 멈추고, 기다림이
    `max.poll.interval.ms` 보다 길면 `ContainerPausingBackOffHandler` 를 쓰라고 한다
    (https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#backoff-handlers).
  - 비용: 다시 시도를 다 쓸 때까지 같은 파티션의 뒤 메시지가 기다린다(`04-consumer-measurement.txt` 의 막는 시간).
- 실패 모음(결정 4, `order.dead-letter.*`·`inventory.dead-letter.*`): 토픽 `<원래 토픽>.DLT`, 파티션 2(원래 메시지와 같은 번호 파티션에 싣는다),
  다시 넣기 그룹 `<소비자 그룹>-dlt-replay`.
- 다시 넣기 명령: `java -jar order-service.jar --order.dead-letter.replay=true --spring.kafka.listener.auto-startup=false`
  (재고 서비스는 `--inventory.dead-letter.replay=true --spring.main.web-application-type=none --spring.kafka.listener.auto-startup=false`).
  명령을 시작한 때 실패 모음 토픽 끝까지를 원래 토픽의 같은 파티션에 같은 키·본문으로 싣고 한 건마다 그룹에 커밋한다. 시험(`OrderServer.replayDeadLetters`,
  `InventoryServer.replayDeadLetters`)은 이 모드로 같은 설정의 서비스를 한 번 띄우고 닫는다.
  - 프로세스가 스스로 끝나는지 확인(후속 작업, 2026-11-22): bootJar 를 따로 띄운 MySQL 8.4·Kafka 4.2.0 컨테이너(시험과 무관)에 붙여 실제 JVM 으로 돌렸다.
    실패 모음에 메시지 1건씩 둔 뒤 주문 서비스 — 3.3초에 뜨고 "Replayed 1 …" 로그 뒤 **스스로 끝남(종료 코드 0, 6초)**, 재고 서비스 — "Replayed 1 …" 뒤 **스스로 끝남(코드 0, 3초)**.
    원래 토픽 0번 파티션에 1건씩 실린 것을 오프셋으로 봤다. 주문 서비스를 한 번 더 돌리자 "Replayed 0 …" 뒤 끝남(코드 0, 3초) — 다시 넣기 그룹이 넘긴 자리를 기억한다.
    코드는 고치지 않았다(끝나게 하는 코드가 따로 필요 없었다).
  - 이 확인에서 본 것: 두 서비스의 설정 파일 이름은 `order-service.properties`·`inventory-service.properties` 라 `--spring.config.name=order-service`
    (재고는 `inventory-service`)를 함께 줘야 한다. 주지 않으면 설정 파일을 읽지 않아 DB 계정이 비어 뜨지 못했다(종료 코드 1). 위 명령과 `DeadLetterReplayCommand` 주석에는
    이 인자가 빠져 있다 — 9장부터 두 서비스를 따로 띄울 때 늘 필요한 인자이고, 주석은 고치지 않았다.
    확인에 쓴 실패 모음 토픽은 서비스보다 먼저 콘솔 프로듀서가 파티션 1개로 만들었고, 서비스가 뜨며 NewTopic(파티션 2)으로 파티션을 늘렸다.
- 11장 계약은 다시 시도를 2·4·4·4초(합 14초)로 줄이고, 받는 쪽 저장소 장애를 6초 낸다.
  - 처음에는 1초씩 3번·장애 1.5초로 두었는데 출발 구조에서도 장애 계약이 통과했다. Spring Kafka 기본 처리(`FixedBackOff(0L, 9)`)의 10번이 이 환경에서 바로 끝나지 않고
    다시 읽을 때마다 약 0.5초씩, 모두 약 4.6초 걸렸기 때문이다(로그의 시도 간격, 출발 구조 실행). 0.5초는 소비자 `fetch.max.wait.ms` 기본값(500ms)과 같다 —
    다시 읽는 요청이 그만큼 기다린 것으로 보이나 확인하지 않았다(가정). 그래서 장애를 기본 처리 시간보다 길고 계약의 다시 시도보다 짧은 6초로 늘렸다.
  - "처리할 수 없는 결과 뒤의 결과는 첫 다시 시도 간격(2초) 안에 반영된다"가 다시 시도하지 않음을 보는 방법이다.

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch11-start`에서 `./gradlew clean test`(별도 worktree)를 돌려 11장 계약 24개를 뽑은 결과.
- `02-final-test-result.txt`: 태그 `ch11`에서 전체 테스트(`./gradlew clean check`)와 커버리지. 주문 서비스·재고 서비스 모듈 시험은 맨 끝에 붙였다(두 모듈에는 커버리지 검사를 걸지 않았다).
- `03-comparison-known-failing-result.txt`: `./gradlew knownFailingTest`(2~11장 비교) + 7장 `jpaVersionTest`.
- `04-consumer-measurement.txt`: `./gradlew consumerMeasurement`(기본 `check` 와 `knownFailingTest` 에서 빠짐 — `test` 의 excludeTags 에 `consumer-measurement`).

### 소비자 계약을 만드는 방법

- 계약: `PaymentEventConsumerContract`(주문 서비스), `StockCommandConsumerContract`(재고 서비스) 각 12개. 운영 `PaymentEventConsumerTest`·`StockCommandConsumerTest`.
- 메시지는 시험이 받는 쪽 토픽에 직접 싣는다(`MessageFeed` — 키 = 결제 번호, Kafka 기본 파티셔너와 같은 계산으로 같은 파티션을 고른다). 이벤트·명령 번호는 결제 서버
  번호와 겹치지 않게 10억대에서 시작한다. "결제 서버가 같은 결과를 다시 보냄" 하나만 결제 서버를 거친다(보낸 결과의 `sent_at` 을 비워 릴레이가 다시 보냄 — 9·10장과 같은 방법).
- 결제 이벤트 토픽은 컨텍스트마다 새 이름(`OrderServerConfiguration`): 이벤트 번호는 컨텍스트(MySQL 컨테이너)마다 1부터라, 같은 토픽을 쓰면 다른 컨텍스트의 이벤트가 같은 번호로
  들어와 결정 1에 걸러진다(10장 재고 명령 토픽과 같은 이유). 다른 결제 서버(`OtherPaymentServer`)에도 같은 토픽을 넘긴다(출발 구조에서 빠뜨려 결정 1 커밋에서 고침).
- "다 처리했다": 받는 쪽 소비자 그룹이 커밋한 자리가 토픽 끝에 닿을 때까지 기다린다(`TopicLag`, `InventoryServer.awaitCaughtUp`).
- 받는 쪽 저장소 장애: MySQL 관리자 계정으로 `set global read_only = on/off`(`ReceiverOutage`). 받는 쪽 계정의 쓰기가 곧바로 오류 1290 으로 실패한다(로그로 봄).
  두 서비스와 결제 서버가 같은 MySQL 컨테이너를 써 함께 막힌다. 연결이 끊기는 장애(연결 풀이 새 연결을 기다림)는 만들지 않았다.
- 한 주문·예약의 변경만 실패: 트리거가 `signal sqlstate '45000'`(`OrderServer.failOrderWrites`, `InventoryServer.failReservationChange`). 일시 오류로 분류된다.
- 실패 모음 읽기: `DeadLetterTopic` 이 소비자 그룹 없이 처음부터 끝까지 읽고 `kafka_dlt-exception-*` 머리를 원인으로 읽는다. 토픽이 없으면 비어 있다.
- 9장 릴레이 계약은 결제 서버가 보낸 것을 주문 서비스의 받은 기록으로 셌는데, 결정 1부터 같은 번호가 한 번만 남아 토픽에서 읽게 바꿨다(`OrderServer.sentEventIds`).

반복 실행으로 흔들리지 않는지 확인했다(같은 코드 27a8aae, `--rerun`): 11장 기본 시험 48개(운영 주문·재고 24 + 재고 결정 1·2 비교 24) 3번 모두 통과
(310·309·310초), 결정 4 커밋의 `clean check` 와 합쳐 4번. 11장 비교 144개는 결정 4 커밋 실행과 docs/ch11/03 실행 두 번의 통과·실패가 144개 모두 같았다.
겹침·실패 시점은 트리거(한 주문·예약의 변경을 실패시킴)와 read_only 전환을 시험이 정한 때에 걸어 고정했다.
시간으로 판정하는 계약이 하나 있다 — "처리할 수 없는 결과 뒤의 결과는 첫 다시 시도 간격(2초) 안에 반영된다". 운영 실행에서 0.09~0.20초였다.

## 비교 구현을 끼우는 방법

- 주문 서비스·재고 서비스 쪽 구성품은 `test.order.components`·`test.inventory.components`(시험 설정)로 그 서비스 컨텍스트에 더한다(`@Primary`).
  Kafka 설정 비교(`@Configuration`)는 결제 서버 시험이 훑는 패키지 밖 `com.example.consumercomparison` 에 둔다.
- 결정 1: 가리지 않음(`NoDedupOrderPaymentService` — 받은 기록의 고유 키를 시험 스키마 `order-no-unique-event.sql` 로 뗌), 상태 전이만(`TransitionOnlyOrderPaymentService`
  — 결제 상태의 허용 전이 가운데 결과 상태끼리), 재고 명령 번호로 가리지 않음(`NoDedupStockCommandService`).
- 결정 2: 거르지 않음(`NoFilterOrderPaymentService`), 만든 시각으로 비교(`OccurredAtOrderPaymentService` — 이 주문에 앞서 받은 이벤트 가운데 만든 시각이 같거나 늦은 것이 있으면
  옛 결과), 재고 옛 명령 거르지 않음(`NoFilterReservationRecorder`). 재고 명령에는 만든 시각이 없어 재고 쪽 시각 비교는 없다.
  결정 1·2 비교는 다른 결정(3·4 포함)은 운영과 같게 둔다.
- 결정 3: Spring Kafka 기본 처리(`SpringDefaultErrorHandling` — `new DefaultErrorHandler()`), 다시 시도 토픽(`RetryTopicOrderConsumer`·`RetryTopicStockConsumer` —
  `@EnableKafkaRetryTopic` + `RetryTopicConfiguration`, 간격·횟수·영구 오류는 운영과 같게, DLT 접미사 `.DLT`, `RetryTopicSchedulerWrapper` 빈이 있어야 뜬다).
- 결정 4: 로그만(`LogOnlyErrorHandling`), DB 실패 표(`FailureTableErrorHandling` — 받는 쪽 자기 DB 의 `failed_message`, 다시 넣기는 시험이 줄을 원래 토픽에 싣고 지움).
- 구현하지 않은 선택지: Kafka 트랜잭션(exactly-once, read_committed) — 받는 쪽 DB 반영까지 묶지 못해 비교로 돌리지 않았다(계획). Spring Kafka 문서의 exactly-once 는
  "read → process → write" 순서를 말하고 DB 는 말하지 않는다(https://docs.spring.io/spring-kafka/reference/kafka/exactly-once.html).

## 인용한 문서(확인한 것 — 2026-11-21 에 읽은 현재 판(4.2.0) 참고 문서. 쓰는 판은 4.1.2 다)

- 기본 처리: "By default, after ten failures, the failed record is logged (at the ERROR level)." / `FixedBackOff(0L, 9)`, 기본으로 다시 시도하지 않는 예외 목록
  (DeserializationException, MessageConversionException, ConversionException, MethodArgumentResolutionException, NoSuchMethodException, ClassCastException)
  — https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#default-eh
- 다시 시도 전 seek: "If retries are not exhausted, perform seeks so that all the remaining records (including the failed record) will be redelivered." — 같은 곳
- 다시 시도 토픽: "By using this strategy you lose Kafka's ordering guarantees for that topic." — https://docs.spring.io/spring-kafka/reference/retrytopic/how-the-pattern-works.html
- 리스너 컨테이너 기본 AckMode 는 BATCH — https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html#committing-offsets
- 확인하지 못한 것(가정): `DeadLetterPublishingRecoverer` 의 기본 토픽 이름(`.DLT` 인지 `-dlt` 인지)과 머리 목록 — 문서를 끝까지 읽지 못했다. 운영 코드는 기본값에 기대지 않고
  토픽·파티션을 직접 정한다(`<토픽>.DLT`, 같은 번호 파티션). 원인 머리(`kafka_dlt-exception-*`)가 실리는 것은 계약 실행으로 봤다.
  기본 처리기의 예외 분류가 원인 사슬(ListenerExecutionFailedException 안의 JacksonException 등)을 따라가는지는 문서에서 찾지 못했다 — 계약(모르는 상태·읽을 수 없는 본문이
  첫 간격 안에 실패 모음으로 감)이 통과한 실행으로만 안다.

## 코드 규칙 예외(사용자 결정 2026-11-22)

`check-conventions.py` 의 11장 새 위반 5건은 앞 장에서 승인한 예외와 같은 예외로 둔다:
- 주문 서비스 domain/error 예외 생성자 3건(OrderServiceException, UnknownPaymentStatusException, InvalidPaymentEventException)
- 주문 서비스 — 웹 진입점이 없는 모듈의 전역 예외 처리기(필수 처리기) 없음 1건
- 재고 서비스 domain/error 예외 생성자 2건(UnknownStockCommandException, InvalidStockCommandException)
결제 서버 21건(domain/error 예외 생성자 20 + RequestRejectedException 1)은 10장과 같다.

## 11장 규칙이 앞 장 비교에 낳은 결과(사용자 결정 2026-11-22 — 비교 코드는 그대로 둠)

9장 결제 이벤트 비교 '커밋 뒤에 보냄'·'그 자리에서 바로 보냄'은 이벤트 고유 번호를 UUID 로 보낸다. 11장 주문 서비스는 숫자가 아닌 번호를 영구 오류로 보아
(결정 3) 주문을 바꾸지 않으므로 두 비교의 실패가 4 → 7, 5 → 7 로 늘었다(docs/ch11/03). 9장 비교 코드는 바꾸지 않았다.
재고 서비스의 결정 1·2 는 이 장 계약 12개에서 10장 상태 전이만으로도 결과가 같았다 — 코드는 그대로 둔다.

## 부하 테스트

k6 는 돌리지 않았다. 받는 쪽 장애 동안의 유실·실패 모음·반영 지연과 막는 시간은 시험 JVM 안에서 잰 `04-consumer-measurement.txt`에 있다.
