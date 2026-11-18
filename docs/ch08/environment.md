# 8장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2(연결 10개, 기본값), Tomcat 11.0.27, Jackson 3.1.7 — 7장과 같다(의존성 변경 없음)
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지, 격리 수준은 기본값 REPEATABLE READ)
- Docker 28.3.0 (Docker 에 잡힌 메모리 15.6GiB, 시험과 상관없는 다른 컨테이너도 함께 실행 중이었다)

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch08-start`(상품·재고·포인트·쿠폰을 고객 금액을 믿고, 승인 뒤 잠그지 않고 읽어 줄이고, 실패해도 되돌리지 않는 출발 코드)에서 8장 테스트를 돌린 결과.
  출발 구조 뒤 결정 4 커밋에 더한 계약 "부분 취소가 쌓여 남은 금액이 0원이 되면 잡아 둔 것이 돌아온다"를 출발 코드에 얹어 다시 돌린 값이다
- `02-final-test-result.txt`: 태그 `ch08`(마지막 기록 커밋)에서 전체 테스트(`./gradlew clean check`)를 돌린 결과와 커버리지
- `03-comparison-known-failing-result.txt`: `./gradlew knownFailingTest`로 계약을 못 지키는 비교 구현을 돌린 결과(2~7장 비교 구현 포함). 뒤이어 도는 7장 `jpaVersionTest` 결과를 맨 끝에 붙였다
- `04-stock-measurement.txt`: `./gradlew stockMeasurement`로 재고를 줄이는 방식마다 재고 100개 상품에 승인 요청 300건을 한꺼번에 보낸 결과

### 시험 컨텍스트 캐시

시험 컨텍스트(설정이 다른 시험 묶음)마다 MySQL 컨테이너가 하나씩 뜬다. Spring 시험 컨텍스트 캐시는 기본 32개까지 컨텍스트를 닫지 않고 둔다.
8장 비교 구현으로 컨텍스트가 늘자, 결정 5 커밋에서 돌린 `./gradlew clean check`가 641개 중 20개 실패했다(모두 웹훅 시험 두 묶음,
"Container is started, but cannot be accessed" — 새 MySQL 컨테이너에 접속하지 못함, 전체 18분 7초). 이 머신에서 컨테이너가 한꺼번에 많이 떠 있던 탓으로 보고
(Docker 메모리 15.6GiB, 다른 컨테이너도 실행 중), 모든 시험 태스크에 `spring.test.context.cache.maxSize=10`을 주어 오래 안 쓴 컨텍스트와 그 컨테이너를 닫게 했다.

### 겹침을 고정하는 방법

7장과 같은 방법이다(기록 붙잡기 `SlowWrites` + processlist 의 `User sleep` 확인, Mock PG 승인 답 붙잡기).

- 같은 상품·같은 쿠폰의 두 주문(`StockContract`, `CouponContract`): 먼저 보낸 주문의 인증 기록(결제 상태를 AUTHENTICATED 로 바꾸는 UPDATE)을 1초 붙잡는다.
  재고·쿠폰을 바꾸는 문장은 JdbcTemplate 으로 바로 나가고, 결제 상태 UPDATE 는 커밋 때 나간다 — 그래서 먼저 보낸 주문은 바꾼 재고 줄·쿠폰 줄의 잠금을
  쥔 채 잠든다. 잠든 것을 확인한 뒤 두 번째 주문의 승인을 보낸다. 두 번째 주문의 첫 일반 읽기는 먼저 보낸 주문이 커밋하기 전이다.
- 거절 답 기록·보정·알림 처리가 겹침(`ReleaseContract`): 카드사가 거절을 남기고 거절 답은 붙잡아 둔다(8장부터 승인 답 붙잡기 문이 거절 답도 붙잡는다).
  거절 알림을 받아 알림 처리가 승인 실패를 남기다 1초 붙잡힌 동안 보정을 시작하고, 붙잡아 둔 거절 답을 보낸다.
- 재고 10개에 주문 30건(`StockContract`)과 측정의 300건은 고정하지 않는다. 미리 준비해 둔 주문의 승인 요청을 스레드에서 래치로 한꺼번에 보낸다.

반복 실행으로 흔들리지 않는지 확인했다(구현 보고에 횟수와 결과).

## Mock PG

8장에서 더한 것:

- 카드사 쪽 만료(`EXPIRED`): 승인 요청이 카드사에 닿지 않은 결제를 시험이 바로 만료시킨다(따로 띄우면 `POST /control/expiries/{orderId}`, 본문 `{paymentKey, amount}`).
  만료한 결제는 주문번호 조회에 `EXPIRED`로 답하고, 취소할 수 없다. 웹훅을 켜 두었으면 `PAYMENT_STATUS_CHANGED` 알림도 보낸다.
- 승인 답 붙잡기(`holdConfirmAnswers`)가 거절 답도 붙잡는다.

토스페이먼츠 문서에서 확인한 것과 확인하지 못한 가정:

- 확인함 — Payment 객체 status: "`EXPIRED`: 결제 유효 시간 30분이 지나 거래가 취소된 상태입니다. `IN_PROGRESS` 상태에서 결제 승인 API를 호출하지 않으면 `EXPIRED`가 됩니다."
  (https://docs.tosspayments.com/reference , Payment 객체) / 웹훅 이벤트: "결제창이 유효한 30분 안에 구매자가 결제창에서 인증을 하지 않거나, 결제 인증이 유효한 10분 안에 상점에서 결제 승인 API를 호출하지 않으면 결제 상태가 `EXPIRED`로 변경됩니다."
  (https://docs.tosspayments.com/reference/using-api/webhook-events#payment_status_changed). Mock PG 는 시간을 기다리지 않고 시험이 바로 만료시킨다.
- 가정(문서에서 확인 못 함) — `EXPIRED`로 바뀔 때 `PAYMENT_STATUS_CHANGED` 웹훅을 보낸다: 문서는 상태가 바뀐다고만 적고, 그 상태 변경에 웹훅이 가는지는 그림(읽지 못함)에 있다.
- 가정(문서에서 확인 못 함) — 주문번호 결제 조회가 만료된 결제도 `EXPIRED`로 돌려준다: 문서는 "승인된 결제를 orderId로 조회합니다."라고만 적는다(4장 Mock PG 가 거절도 조회로 돌려준다고 가정한 것과 같은 가정).
- 결제 서버의 우리 쪽 `EXPIRED` 상태를 만드는 길은 지금 코드에 없다(`Payment.expire()`를 부르는 곳이 없음). 카드사 만료는 웹훅·보정이 승인 실패(`APPROVAL_FAILED`)로 닫는다(6장부터의 규칙).

## 비교 구현을 끼우는 방법

- 결정 1(금액): 계약의 준비 진입점을 바꾼다 — 고객 금액을 믿는 비교는 받은 금액으로 결제만 남긴다(담긴 상품은 남기지 않음, 금액만 보는 비교).
- 결정 2(잡는 때): 운영 `PaymentHoldings`를 인증 때 잡지 않는 `DeferredHoldings`로 바꿔 끼우고, 준비 뒤·승인 뒤에 같은 방법으로 잡는다. 승인 뒤 잡기는 모자라면 운영 취소 서비스로 전액 취소한다.
- 결정 3(재고 줄이기): 재고 저장소만 우선 빈으로 바꾼다. 변경 번호는 시험 DB 에만 `stock.version` 칸을 더하고(`stock-version.sql`), 어긋나면 승인을 처음부터 다시 요청한다(최대 30번) —
  같은 트랜잭션 안에서 다시 읽으면 REPEATABLE READ 스냅숏이라 같은 값을 다시 본다.
- 결정 4(되돌리기): `PaymentHoldings`를 곳마다 따로 되돌리는 것으로, 또는 포인트 저장소를 이력 없이 잔액만 고치는 것으로 바꾼다(둘을 함께 끼운 비교도 있다).
- 결정 5(쿠폰): 쿠폰 저장소만 바꾼다. 사용 기록 비교는 시험 DB 에만 `coupon_usage`를 둔다(`coupon-usage.sql`).
- 상품·재고·회원 포인트·쿠폰은 시험이 직접 남긴다(`Shop`). 등록 API 는 이 연재의 범위 밖이다.

## 부하 테스트

k6 는 돌리지 않았다. 재고를 줄이는 방식마다의 응답 시간·처리량은 서비스를 시험 JVM 안에서 바로 불러 잰 `04-stock-measurement.txt`에 있다.
