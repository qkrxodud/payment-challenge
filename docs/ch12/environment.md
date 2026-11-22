# 12장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2(연결 10개, 기본값), Tomcat 11.0.27, Jackson 3.1.7,
  Spring Kafka 4.1.2, kafka-clients 4.2.2, 브로커 `apache/kafka:4.2.0` 한 대 — 11장과 같다
- 12장에서 더한 것: Resilience4j 2.4.0(`resilience4j-circuitbreaker` — 핵심 모듈만), Spring Boot Actuator 4.1.2, Micrometer 1.17.2(세 서비스 모두)
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지, 격리 수준은 기본값 REPEATABLE READ, 컨테이너 시간대 UTC)
- Docker 28.3.0 (시험과 상관없는 다른 컨테이너도 함께 실행 중이었다)
- 출발점: 원격 main = chapter-11 = 27a5b8a(태그 ch11)
- 긴 실행은 `caffeinate -dims` 로 돌렸다.

## 모듈과 DB

- 결제 서버(12장에서 바뀜):
  - 결정 1: `reconciliation_result`(대사 결과, (날짜, 결제 키, 종류) 고유 키 — insert … on duplicate key update), job_lock 줄 `payment-daily-reconciliation`.
    우리 결제는 JDBC 한 문장(결제 + 끝난 취소의 합)으로 읽는다(엔티티로 읽지 않음, 잠그지 않음).
  - 결정 3: `payment_status_history`(결제 외래 키, (payment_id, id) 인덱스). 결제를 지우는 앞 장 시험 도구 4곳이 이력을 먼저 지운다.
  - 결정 5: job_lock 줄 `payment-outbox-cleanup`. 새 칸은 없다.
- `mock-pg`: 거래 조회 `GET /v1/transactions`, 결제 기록에 승인 거래 키·처리한 때, 거래 조회 횟수(`/control/transaction-lists`)와 답 붙잡기(시험), 측정용 `forgetPayments`.
- `order-service`·`inventory-service`(결정 5): Actuator·Micrometer, 실패 모음 수 지표, 받은 기록·처리한 명령 정리(매일). 표·칸은 그대로다.
- 이미 만든 DB 에는 `create table if not exists` 로 새 표 두 개(`reconciliation_result`, `payment_status_history`)와 job_lock 두 줄(insert ignore)이 더해진다.
  옛 결제에는 이력이 없다(12장 이후 전이부터 남는다).

## 시각을 남기는 방식(관찰)

같은 MySQL(컨테이너 시간대 UTC)에 시각을 남기는 방식이 칸마다 다르다 — 12장 코드는 그 칸을 남긴 쪽과 같은 방식으로 견준다.
- JPA(Hibernate)가 남긴 `Instant`(결제 `authenticated_at`, outbox `occurred_at`, 이력 `changed_at`): UTC 시각. JDBC `getTimestamp` 로 읽으면 이 JVM 시간대(KST)로 읽혀 9시간 어긋난다.
- JDBC `Timestamp.from`(outbox `sent_at` — 9장 `markSent`, 주문 서비스 `received_at`, job_lock): 이 JVM 시간대 시각.
- 재고 서비스 `processed_at`: `LocalDateTime.ofInstant(…, UTC)` — UTC 시각.
처음에 대사가 인증한 때를 `Timestamp` 로 넘겨 9시간 어긋났다(결정 3 커밋에서 고침). 앞 장 칸의 방식은 바꾸지 않았다.

## 토스페이먼츠 거래 조회(결정 1 — 확인한 것)

https://docs.tosspayments.com/reference#거래-조회 (2026-11-22 읽음)
- `GET /v1/transactions`. `startDate`·`endDate` 필수, ISO 8601 `yyyy-MM-dd'T'HH:mm:ss`. "거래 기록은 거래가 처리된 시점(`transactionAt`)을 기준으로 조회합니다."
- `startingAfter`(거래 키 — 그 뒤부터), `limit` 기본 100, "최대값은 `5000`입니다." 많은 기록을 쪽으로 나눠 처리할 때 쓴다.
- 요청이 60초까지 걸릴 수 있어 시간 제한을 60초 이상으로 두라고 한다(운영 `payment.pg.transactions-read-timeout=60s`). 라이브에서 요청이 많으면 429.
- Transaction 객체: mId, transactionKey, paymentKey, orderId, method, customerKey, useEscrow, receiptUrl, status, transactionAt(오프셋 붙은 ISO 8601), currency, amount.
- status: READY, IN_PROGRESS, WAITING_FOR_DEPOSIT, DONE, CANCELED, PARTIAL_CANCELED, ABORTED, EXPIRED.
- "결제 승인, 취소, 부분 취소가 이루어질 때마다 각각의 거래 정보를 담은 Transaction 객체가 만들어지고 개별 `transactionKey`를 가지게 됩니다."
확인하지 못해 가정한 것(Mock PG 와 대사 코드가 이 가정 위에 있다):
- 거래의 status 는 그 거래가 끝난 뒤의 결제 상태다(승인 거래 DONE, 취소 거래는 잔액이 남으면 PARTIAL_CANCELED, 없으면 CANCELED).
- 취소 거래의 amount 는 그 취소 금액이다(문서는 "결제 금액"이라고만 적는다).
- 거절(ABORTED)·만료(EXPIRED)는 거래로 나오지 않는다(문서가 승인·취소·부분 취소만 든다).
- 날짜에 시간대가 없어 한국 시각으로 읽고, endDate 는 그 초까지 넣는다(문서 예 23:59:59), 순서는 처리한 때 순, 5000 을 넘는 limit 은 5000 으로 줄인다.
- 어제 거래가 언제 모두 나오는지(대사 cron 4시)는 확인하지 못했다.
- 거래 내역에는 매입 단계가 없다(status 목록) — 1장이 남긴 "대사에 매입 상태가 다시 필요한가"는 이 대사에서는 필요하지 않았다. 운영 코드에는 `capture()` 를 부르는 흐름이 없다
  (시험만 부른다). 대사는 매입(CAPTURED)을 승인과 같은 단계로 본다(단위 시험 "매입한 결제는 카드사 승인과 같은 단계로 본다").

## 대사(결정 1·2)

- 그날 = `payment.daily-reconciliation.zone`(Asia/Seoul)의 하루. 카드사: 그날 0시~23:59:59 거래를 5000건씩 모두 읽어 결제 키로 묶는다.
  우리: 그날 인증한 결제(결제 키 있음) + 그날 카드사 거래가 있는 앞날 결제(결제 키로 찾음).
- 견주기(Mismatch.between): 금액(그날 승인 거래가 있을 때) → 단계(기다림 < 승인 < 부분 취소 < 취소, 승인 없이 끝남은 줄 밖) → 같은 단계면 남은 금액(그날 승인 거래가 있을 때).
  우리 단계가 뒤면 STATUS_BEHIND, 앞이거나 승인 없이 끝났는데 카드사는 승인이면 STATUS_MISMATCH. 카드사 거래가 없는 우리 결제는 승인이 있었던 결제만 OURS_ONLY
  (결과 모름·인증 상태는 4장 보정 몫). 우리에게 결제 키가 없으면 PG_ONLY.
- 결정 2: STATUS_BEHIND 만 카드사 결제를 다시 조회해 6장 따라가기(`PaymentWebhookRecorder.follow`, 계기 DAILY_RECONCILIATION)로 맞추고 FOLLOWED.
  조회에 답이 없으면 NEEDS_REVIEW 로 남기고 다음 대사가 다시 맞춘다(같은 줄을 고쳐 씀). 나머지는 NEEDS_REVIEW. 운영자가 확인한 줄을 닫는 길은 만들지 않았다.
- 서버 두 대: 같은 job_lock. 계약은 Mock PG 거래 조회 답을 붙잡아(래치) 한 서버가 조회 중일 때 다른 서버가 대사를 부르게 고정했다.
- 시험에서 알림을 모두 실패시키는 방법: Mock PG 알림 주소를 `http://localhost:1/…`(받는 곳 없음)로 두고 7번 재전송(시험 간격 0.1초)이 끝날 때까지 기다린다.

## 상태 이력(결정 3)

칸: `from_status`, `to_status`, `action`(허용 전이 표의 행위), `changed_by`(계기: CONFIRM, APPROVAL_RECONCILIATION, WEBHOOK, CANCEL, CANCEL_RECONCILIATION,
DAILY_RECONCILIATION — `trigger` 는 MySQL 예약어라 피함), `changed_at`(기록한 서버 시각). 결제가 바뀐 전이를 엔티티 밖(저장하지 않는 목록)에 모으고,
결제를 바꾼 기록이 같은 트랜잭션에서 꺼내 남긴다. 결제 생성(준비)은 전이가 아니라 남기지 않는다. 시험 비교 구현(앞 장)이 결제를 바꾸면 이력은 남지 않는다.

## 카드사 장애(결정 4)

- Resilience4j: https://resilience4j.readme.io/docs/circuitbreaker (2026-11-22 읽음) — 기본값 failureRateThreshold 50, slidingWindowType COUNT_BASED,
  slidingWindowSize 100, minimumNumberOfCalls 100, waitDurationInOpenState 60000ms, permittedNumberOfCallsInHalfOpenState 10.
  "The CircuitBreaker rejects calls with a `CallNotPermittedException` when it is OPEN." 열린 시간이 지나면 HALF_OPEN 으로 바뀌어 정한 수만 시험 삼아 보낸다
  (자동 전환을 켜지 않으면 그 뒤 첫 호출 때 바뀐다). minimumNumberOfCalls 만큼 기록되기 전에는 실패율을 계산하지 않는다.
- 버전: Maven Central 에 `resilience4j-spring-boot4` 2.4.0(2026-03)이 있고 그 pom 은 Spring Boot 4.0.0·Spring 7.0.2 에 맞춰져 있다. 4.1.2 와 맞는지는 확인하지 않아
  Spring Boot 연동 모듈을 쓰지 않고 `resilience4j-circuitbreaker` 2.4.0(의존: resilience4j-core, slf4j-api)만 썼다. 클래스 파일 버전 61(Java 17)을 확인했다.
- 운영 값(application.properties): 최근 20번 중 50% 이상 실패(최소 10번)면 열고 30초 뒤 3번을 시험 삼아 보낸다.
  계산: 열리기 전까지 결과 모름이 될 수 있는 승인은 많아야 minimumNumberOfCalls(10)번과 그때 이미 보낸 승인이다(측정 04 에서 고객 20명일 때 20건).
  값 자체(10·20·50%·30초)는 잰 장애 통계가 없어 가정이다.
- 실패로 세는 것: 답 없음(읽기·연결 시간 초과), 카드사 오류(5xx). 거절(4xx)은 성공으로 센다. 열리면 `PaymentGatewayUnavailableException`(503) — 결제는 승인 실패로 닫고
  잡아 둔 재고·쿠폰·포인트를 돌려준다(10장 결정 4 재고 답 없음과 같은 길). 재고 예약은 승인 요청 전에 하므로, 열린 동안에도 재고를 잡았다가 돌려준다(관찰).
- 같은 차단기를 쓰지 않는 곳: 취소·조회·보정·대사(판단 — 근거는 결정 4 커밋, 보고의 확인 필요).
- 시험은 차단기를 기본으로 끈다(`payment.pg.circuit-breaker.enabled=false`, build.gradle). 앞 장 시험은 한 컨텍스트에서 카드사 장애를 여러 번 만들어 켜 두면 차단기가 열려
  뒤 시험이 막힌다. 차단기 계약과 측정만 켠다. 계약 값: 최근 4번 중 50%(최소 4번), 2초, 시험 삼아 1번.

## 운영 신호와 정리(결정 5)

- 지표(Micrometer 1.17.2): 결제 서버 `payment.outbox.unsent`, `payment.outbox.oldest-unsent-age`(초), `payment.reconciliation.open-mismatches`, `payment.pg.circuit.state`,
  주문 서비스 `order.dead-letter.pending`, 재고 서비스 `inventory.dead-letter.pending`. 모두 게이지 — 읽을 때마다 DB 집계 한 문장 또는 브로커 조회(5초 제한)로 새로 센다.
  Micrometer 문서: "A gauge is a handle to get the current value.", 게이지는 관찰하는 객체를 약한 참조로만 쥔다 — 여기서는 Spring 빈이라 컨텍스트가 쥔다
  (https://docs.micrometer.io/micrometer/reference/concepts/gauges.html).
- 노출: Spring Boot 는 기본으로 health 만 HTTP·JMX 로 내보낸다("By default, only the health endpoint is exposed over HTTP and JMX.",
  https://docs.spring.io/spring-boot/reference/actuator/endpoints.html). 결제 서버·재고 서비스는 `management.endpoints.web.exposure.include=health,metrics`
  로 `/actuator/metrics/{이름}` 을 연다. 주문 서비스는 웹 서버가 없어(9장) 지표가 서비스 안의 보관함에만 있다 — 밖으로 내보내는 길은 정하지 않았다(확인 필요).
  Prometheus 등 수집기는 붙이지 않았다. 시험은 각 서비스 컨텍스트의 `MeterRegistry` 에서 읽는다.
- 실패 모음 수 = 파티션마다 (토픽 끝 − 다시 넣기 그룹이 커밋한 자리, 커밋이 없으면 토픽 처음). 다시 넣기 명령이 한 건마다 그 그룹에 커밋한다(11장) — 다시 넣은 메시지는 빠진다.
- 정리: 결제 서버 7일 전에 보낸 outbox 줄(작업 잠금), 주문·재고 서비스 14일 전 받은/처리한 기록. 매일 4시 30분, `delete … order by id limit 1000` 을 되풀이.
  근거: 받는 쪽 토픽·실패 모음은 브로커 기본 보존 기간을 쓴다 — Kafka 문서 log.retention.hours 기본 168(7일)
  (https://kafka.apache.org/42/configuration/broker-configs/). 14일은 그 두 배(가정), 7일은 같은 값(가정).
  지운 뒤 같은 메시지가 다시 와도 11장 결정 2(마지막으로 반영한 번호)가 주문·예약을 바꾸지 않는다(계약 "받은 결제 결과가 정리된 뒤 같은 결과가 다시 와도 주문은 다시 바뀌지 않는다").
  계약에서 깨진 것은 없었다. 코드로 본 것(시험으로 세지 않음): 지운 뒤 다시 온 결과는 받은 기록에 한 줄 다시 남는다(지운 줄 대신 — 같은 번호가 두 줄이 되지는 않는다).
  가리는 일을 보존 기간 안에서는 결정 1이, 그 뒤에는 결정 2가 한다. 결정 2는 주문·예약 줄에 기대므로 그 줄을 지우면 깨진다 — 주문·예약 줄은 지우지 않는다.
  지운 outbox 번호가 다시 쓰이지 않는 근거: MySQL 8.4 문서 "The current maximum auto-increment value is persisted, preventing the reuse of previously allocated values."
  다만 "in the case of an unexpected server exit, reuse of a previously allocated auto-increment value cannot be guaranteed."
  (https://dev.mysql.com/doc/refman/8.4/en/innodb-auto-increment-handling.html). 비정상 종료 뒤 번호가 다시 쓰이면 받는 쪽이 새 메시지를 옛 번호로 보고 버릴 수 있다 —
  재현하지 않았다.
- 시험은 세 정리 일정을 끈다(build.gradle `payment.outbox.cleanup.enabled`, `order.cleanup.enabled`, `inventory.cleanup.enabled`).

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch12-start`에서 `./gradlew clean test`(별도 worktree)를 돌려 12장 계약 41개를 뽑은 결과.
- `02-final-test-result.txt`: 태그 `ch12`에서 전체 테스트(`./gradlew clean check`)와 커버리지. 주문 서비스·재고 서비스 모듈 시험은 맨 끝에 붙였다(두 모듈에는 커버리지 검사를 걸지 않았다).
- `03-comparison-known-failing-result.txt`: `./gradlew knownFailingTest`(2~12장 비교) + 7장 `jpaVersionTest`.
- `04-operations-measurement.txt`: `./gradlew operationsMeasurement`(기본 `check` 와 `knownFailingTest` 에서 빠짐 — `test` 의 excludeTags 에 `operations-measurement`).

### 12장 계약을 만드는 방법

- 대사: 카드사 쪽 어긋남은 Mock PG 로(가맹점 관리 화면 취소 `cancelAtCardCompany`, 카드사에만 남는 승인 `approveAtCardCompany`), 우리 쪽 어긋남은 결제 줄을 시험이 직접 고쳐
  (금액·상태 update, 카드사가 모르는 승인 결제 insert) 만든다. 서버 두 대는 7장 `OtherPaymentServer`, 겹침은 거래 조회 답 붙잡기(래치)로 고정.
- 이력: 결과 모름은 카드사 답 1.5초·읽기 제한 1초(4장 계약과 같음), 되돌려진 승인은 4장의 `block_approval_record` 트리거.
- 장애: Mock PG LOST 3초·읽기 제한 1초로 승인 4번을 실패시켜 차단기를 연다(계약 값). 열린 2초 뒤 다시 승인.
- 운영 신호: 실패 모음은 받는 쪽 기록을 트리거로 실패시키고 다시 시도를 0.1초 한 번으로 줄여 만든다(계약 값). 지표는 각 서비스 컨텍스트의 `MeterRegistry` 에서 읽는다.
  보존 기간은 줄의 시각을 그 줄을 남긴 방식(위 "시각을 남기는 방식")대로 8·6·15·13일 전으로 고친다.
- 시간으로 판정하는 계약: "차단기가 열린 동안 승인 요청은 … 바로 실패한다"(0.3초 안 — 운영 실행에서 수 ms), "열린 시간이 지나면 다시 승인된다"(2.2초 기다림).

## 비교 구현을 끼우는 방법

- 결정 1: 결제마다 카드사 단건 조회(`PerPaymentInquiryReconciler` — 그날 인증한 우리 결제마다 `findPayment` + 6장 따라가기, 카드사에 없는 승인 결제는 OURS_ONLY).
- 결정 2: `MismatchResolver` 를 바꾼다 — 기록만(`RecordOnlyResolver`), 모두 카드사에 맞춤(`FollowAllResolver` — 결제 줄을 직접 고치고 카드사에만 있는 결제를 새로 남김).
- 결정 3: `PaymentHistories` 를 아무것도 남기지 않게(`NoStatusHistory`), outbox 로 대신함은 그 위에서 payment_outbox 결제 결과 줄을 이력으로 읽는다.
- 결정 4: `PaymentGateway` 를 바꾼다 — 시간 제한만(`TimeoutOnlyPaymentGateway`, 열리지 않는 차단기), 다시 시도(`RetryingPaymentGateway` — 답 없음·오류면 두 번 더).
- 결정 5: 지표 없음·지우지 않음은 운영 코드를 그대로 두고 시험 연결(지표 읽기·정리 부르기)만 비운다 — 11장까지와 같은 상태다.
- 구현하지 않은 선택지: 차단기 대신 동시 승인 수 제한(bulkhead), 웹훅 실패를 카드사에 다시 요청 — 계획에 없어 비교로 돌리지 않았다.

## 코드 규칙

`check-conventions.py`: 결제 서버 22건(11장 21건 + 새 domain/error 예외 생성자 `PaymentGatewayUnavailableException` 1건 — 승인된 예외 범주), 주문 서비스 4건·재고 서비스 7건(11장과 같음).
`check-display-names.sh`, `check-records.sh` 통과.

## 부하 테스트

k6 는 돌리지 않았다. 카드사 장애와 대사 측정은 시험 JVM 안에서 잰 `04-operations-measurement.txt`에 있다.
