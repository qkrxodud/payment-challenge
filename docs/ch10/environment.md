# 10장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2(연결 10개, 기본값), Tomcat 11.0.27, Jackson 3.1.7,
  Spring Kafka 4.1.2, kafka-clients 4.2.2, 브로커 `apache/kafka:4.2.0` 한 대 — 9장과 같다
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지, 격리 수준은 기본값 REPEATABLE READ)
- Docker 28.3.0 (시험과 상관없는 다른 컨테이너도 함께 실행 중이었다)
- 출발점: 9장 코드 위의 패키지 정리 두 커밋(예외·오류 코드 global/error → domain/error, 주기 작업 잠금 창구 domain → application)

## 모듈과 DB

- 루트: 결제 서버. `mock-pg`: 가짜 PG. `order-service`: 주문 서비스(9장과 같음).
- `inventory-service`(10장): 8장에 결제 서버 안에 있던 재고(`stock`)를 떼어 낸 재고 서비스. 결제 서버가 HTTP 로 재고를 예약하고(`POST /reservations`,
  예약 번호 = 결제 번호), 예약을 조회할 수 있다(`GET /reservations/{id}`). 확정·해제는 재고 명령 토픽(`stock-reservation-commands`, 파티션 2개,
  키 = 결제 번호)으로 받는다. 예약은 `stock_reservation`(잡음 RESERVED → 확정 CONFIRMED / 해제 RELEASED)과 `stock_reservation_item`.
  웹 진입점이 있어 전역 예외 처리기를 둔다. 상품(가격)은 결제 서버에 남는다.
- 재고 서비스의 DB 는 결제 서버와 **같은 MySQL 컨테이너의 다른 스키마**(`inventory_service`)를 **다른 계정**으로 쓴다(9장 주문 서비스와 같은 방식).
  스키마와 계정은 시험이 MySQL 관리자 계정으로 만든다(`InventoryServer`).
- 결제 서버의 `payment_outbox`에 `type` 칸을 더했다: `PAYMENT_RESULT`(9장 결제 결과), `STOCK_CONFIRM`·`STOCK_RELEASE`(재고 명령). 결제 서버의 `stock` 표는 걷어 냈다.

## 설정 값

- 재고 서비스 시간 제한(결제 서버, 결정 1): 연결 300ms, 읽기 1s(`payment.inventory.*`). 출발 구조는 PG 와 같은 1s·5s 였다. 근거는 `04-saga-measurement.txt`의
  예약 응답 시간 줄과 01_impl_report 를 본다.
- 10장 계약 시험(`SagaContract`)은 읽기 시간 제한을 0.5초로 줄이고, 늦은 재고 서비스·카드사는 1.5초 뒤에 답한다. PG 읽기 1초, 보정 대기 10초.
- 8장 같은 상품 겹침 시험(`StockContract`)은 재고 서비스의 재고 줄이기를 1초 붙잡는데, 운영 시간 제한(1초)에 걸려 두 주문이 다 그만두어 그 시험만
  읽기 시간 제한을 5초로 둔다.

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch10-start`에서 `./gradlew clean test`를 돌려 10장 계약(`SagaTest`)과 실패한 앞 장 시험을 뽑은 결과.
- `02-final-test-result.txt`: 태그 `ch10`에서 전체 테스트(`./gradlew clean check`)와 커버리지. 재고 서비스·주문 서비스 모듈 시험은 맨 끝에 따로 붙였다
  (두 모듈에는 커버리지 검사를 걸지 않았다 — mock-pg 와 같다).
- `03-comparison-known-failing-result.txt`: `./gradlew knownFailingTest`(2~10장 비교 구현). 뒤이어 도는 7장 `jpaVersionTest` 결과를 맨 끝에 붙였다.
- `04-saga-measurement.txt`: `./gradlew sagaMeasurement`.

### 재고 서비스와 중계(InventoryLink)

- 시험 컨텍스트마다 재고 서비스를 시험 JVM 안에 하나 띄운다(`InventoryServer`, `InventoryServerConfiguration`). 결제 서버는 시험 JVM 안의 TCP 중계를 거쳐 부른다.
  - 끊기(`cut`): 열린 연결을 모두 닫고, 되돌릴 때까지 새 연결도 받자마자 닫는다.
  - 답 늦추기(`slowAnswers`): 재고 서비스가 보낸 답을 정한 시간만큼 붙잡는다 — 재고 서비스는 이미 처리를 끝냈다.
  - 요청 붙잡기(`holdRequests`/`releaseRequests`): 결제 서버가 보낸 요청을 재고 서비스에 넘기지 않고 쥐고 있다가 나중에 넘긴다. 결제 서버가 기다리다
    연결을 닫아도 쥔 요청은 재고 서비스에 닿는다(늦게 도착한 요청). 넘긴 요청에 재고 서비스가 답할 때까지 기다릴 수 있다(`awaitDelivered`).
- 재고 서비스가 멈춘 것(`down`)은 중계를 끊고 재고 서비스의 Kafka 리스너 컨테이너를 멈춰 만든다. 되돌리면(`up`) 읽던 자리부터 다시 읽는다.
- 재고 서비스가 예약을 처리하는 중인 것은 재고 서비스 DB 의 재고 줄이기를 트리거로 1초 붙잡아 만든다(7장 SlowWrites 와 같은 방법, 재고 줄의 잠금을 쥔 채).
- 재고 명령 토픽은 컨텍스트마다 새 이름으로 만든다. 결제 번호는 컨텍스트(MySQL 컨테이너)마다 1부터 다시 매겨져, 같은 토픽을 쓰면 다른 컨텍스트의
  해제 명령이 이 재고 서비스에 빈 해제 줄을 남겨 같은 번호의 예약을 막는다. 소비자 그룹도 띄울 때마다 새로 정한다.
- "결국"의 끝: 재고를 읽기 전에(`Shop.stockOf` 등) 결제 서버의 `payment_outbox`에 남은 것을 모두 보내고(릴레이를 직접 부름) 재고 서비스의 소비자 그룹이
  읽은 자리가 토픽 끝에 닿을 때까지 기다린다(Kafka Admin 으로 비교). 기본 시험은 9장처럼 릴레이 주기 실행을 끈다.
- 같은 명령이 두 번 가는 것: 그 주문의 결과를 모두 보낸 뒤 `payment_outbox.sent_at`을 비워 다시 보내게 한다(9장 적어도 한 번과 같은 결과).
- 재고 서비스 쪽 비교 구성품은 `test.inventory.components`(시험 설정)로 재고 서비스 컨텍스트에 더한다. 웹 진입점이 있는 구성품은 결제 서버 시험이 훑는
  패키지 밖(`com.example.inventorycomparison`)에 둔다.

반복 실행으로 흔들리지 않는지 확인했다(같은 코드 c6bfb34): 10장 기본 시험 42개(SagaTest·ChoreographySagaTest) 5번 모두 통과,
10장 비교 105개 3번 — 16·16·18개 실패. 겹침을 고정한 계약은 3번 모두 같았고, 다른 것은 몰림 계약(서버 두 대·300건, 겹침을 고정하지 않음) 하나다:
실패로만(87)·수량만(102)이 세 번째 실행에서만 실패했다 — 0.5초를 넘긴 예약이 몇 건 나느냐에 달렸다.

## 비교 구현을 끼우는 방법

- 결정 1(승인 먼저): `ApproveFirstConfirmService`가 카드사 승인 뒤 Kafka(`<명령 토픽>-requests`)로 차감 요청, 재고 서비스 쪽 `ApproveFirstStockListener`가
  수량만 줄이고 답(`-replies`), 결제 서버 쪽 `ApproveFirstReplyListener`가 모자라다는 답에 카드사 전액 취소. 계약 시험은 승인 답 뒤 재고 서비스의 답이 처리될 때까지
  기다린 뒤 다음으로 간다(측정은 기다리지 않는다).
- 결정 2(코레오그래피): 결제 서버는 확정·해제를 남기지 않고(`ChoreographyHoldings`), 재고 서비스 쪽 `PaymentEventReactionListener`가 결제 이벤트를 읽어 확정·해제.
  결제 이벤트 토픽은 컨텍스트마다 새 이름.
- 결정 3(수량만): 예약 대신 `POST /stocks/decrease`(`QuantityStockReservations`, 재고 서비스 쪽 `QuantityStockController`), 해제는 같은 릴레이로 수량을 실어
  (`QuantityStockCommandSender` → `-quantity` 토픽 → `QuantityStockCommandListener`가 늘림).
- 결정 4: 실패로만 봄(`NoReleaseOnTimeoutRecorder` — 답이 없으면 해제 없이 닫음), 조회로 확인(`InquiringHoldings` — 답이 없으면 `GET /reservations/{id}`).
- 결정 5(그 자리에서 HTTP): `DirectHttpHoldings`가 상태를 바꾼 트랜잭션 안에서 HTTP 확정·해제(재고 서비스 쪽 `ReservationCommandController`), 실패하면 로그만.

## 부하 테스트

k6 는 돌리지 않았다. 방식마다의 응답 시간·재고 어긋남과 예약 응답 시간은 시험 JVM 안에서 잰 `04-saga-measurement.txt`에 있다.
