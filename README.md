# 결제 시스템 챌린지

단일 서버 Spring Boot 결제에서 출발해, 장마다 현재 구조의 한계를 테스트로 재현하고 해결하면서 구조를 키워 나가는 연재의 코드입니다.

## 장별 코드

각 장을 마친 시점의 코드 전체를 장별 브랜치에 그대로 둡니다. 브랜치를 고르면 그 장의 코드만 보이고, 그 상태로 바로 빌드됩니다. 장 제목은 그 장을 설명한 블로그 글로 이어집니다(연재 목록: [결제 시스템 챌린지](https://qkrxodud.github.io/tech-blog/series/payment-challenge/)).

- [`chapter-01`](https://github.com/qkrxodud/payment-challenge/tree/chapter-01) — 1장 [결제 기초: 인증, 승인, 매입, 취소와 상태 머신](https://qkrxodud.github.io/tech-blog/posts/payment-state-machine/)
- [`chapter-02`](https://github.com/qkrxodud/payment-challenge/tree/chapter-02) — 2장 [가장 단순한 결제 구현과 트랜잭션 경계](https://qkrxodud.github.io/tech-blog/posts/payment-transaction-boundary/)
- [`chapter-03`](https://github.com/qkrxodud/payment-challenge/tree/chapter-03) — 3장 [결제 금액 검증과 멱등성](https://qkrxodud.github.io/tech-blog/posts/payment-amount-idempotency/)
- [`chapter-04`](https://github.com/qkrxodud/payment-challenge/tree/chapter-04) — 4장 [PG 응답을 받지 못한 결제와 보정](https://qkrxodud.github.io/tech-blog/posts/payment-approval-unknown/)
- [`chapter-05`](https://github.com/qkrxodud/payment-challenge/tree/chapter-05) — 5장 [결제 취소와 부분 취소](https://qkrxodud.github.io/tech-blog/posts/payment-partial-cancel/)
- [`chapter-06`](https://github.com/qkrxodud/payment-challenge/tree/chapter-06) — 6장 [PG 웹훅 처리](https://qkrxodud.github.io/tech-blog/posts/payment-webhook/)

`main`에는 가장 최근 장까지의 코드가 있습니다. 각 장의 출발 구조(문제를 재현하는 코드)와 완성 코드는 아래 태그로도 볼 수 있습니다.

- `ch01-start` — 1장 출발 구조: 상태를 검사하지 않는 변경 메서드 (전이 테스트 실패)
- `ch01` — 1장 결제 상태 머신: 허용 전이 표, 비교 구현(State 패턴, Spring Statemachine), 상태 컬럼 저장 방식
- `ch02-start` — 2장 출발 구조: 인증 기록·PG 승인 호출·결과 기록을 트랜잭션 하나로 묶은 승인 API (승인 테스트 실패)
- `ch02` — 2장 트랜잭션 경계: PG 호출을 트랜잭션 밖으로, 거절도 기록, PG 시간 제한, Open Session In View 끄기, 비교 구현
- `ch03-start` — 3장 출발 구조: 고객이 보낸 금액을 그대로 PG에 보내는 승인 API (금액 변조·재요청·재준비 테스트 실패)
- `ch03` — 3장 결제 금액 검증과 멱등성: 준비 금액과 값 비교, 결제 키로 다시 온 승인 요청 가리기, 같은 주문 재준비, 비교 구현
- `ch04-start` — 4장 출발 구조: 카드사 답을 받지 못한 결제를 다루지 않는 승인 API, Mock PG 결제 조회·취소 (결과 모름 보정·전이·API 테스트 실패)
- `ch04` — 4장 PG 응답을 받지 못한 결제와 보정: 결과 모름 상태, 카드사 조회 보정, 주기 보정과 대기 시간, 기록하지 못한 인증 상태 보정, 다시 온 요청에 202, 비교 구현(망취소, 아무것도 하지 않음, 요청 안에서 바로 조회, 기록 재시도)
- `ch05-start` — 5장 출발 구조: 받은 금액으로 카드사에 취소를 보내고 결제 상태만 바꾸는 취소 API, Mock PG 부분 취소·취소 이력·잔액·멱등키 (부분 취소·같은 요청 가리기·결과 모름 테스트 실패)
- `ch05` — 5장 취소와 부분 취소: 취소마다 한 줄(`payment_cancel`), 남은 금액으로 받고 0원이면 취소 상태, `Idempotency-Key` 필수와 같은 키 재요청·다른 내용 422, 취소 요청 먼저 기록과 결과 모름 보정, 카드사에도 멱등키 전달, 비교 구현(자연 키, 지금 상태로 답함, 카드사 먼저 부르고 기록, 카드사에 멱등키 안 넘김)
- `ch06-start` — 6장 출발 구조: 웹훅을 받아 본문의 결제 상태를 허용 전이 표대로 그 자리에서 옮기는 엔드포인트, Mock PG 결제 상태 웹훅·재전송·시험용 조작 (순서 바뀜·중복·위조·모르는 주문·늦은 조회·결과 모름·카드사에서만 일어난 취소 테스트 실패)
- `ch06` — 6장 PG 웹훅 처리: 알림은 다시 보라는 신호로만 쓰고 카드사 조회로 맞춤, 갈 수 있는 방향이면 따라가고 지난 상태는 무시, 카드사 취소 이력을 transactionKey 로 맞춰 처리 자체를 멱등으로, 받은 알림을 남기고 바로 200 뒤 주기 처리기, 결과 모름 결제·취소를 알림으로 바로 맞추고 카드사에서만 일어난 취소를 들여옴, 비교 구현(본문 믿기, 표에 없으면 거부, createdAt 으로 최신만, 본문 해시로 거르기, 요청 안에서 조회·반영, 알림 무시)
- `ch07-start` — 7장 출발 구조: 6장 코드 그대로(잠금 없음, 준비는 찾아서 없으면 남김, 인증 상태 결제에 같은 키는 409, 주기 작업은 서버마다 돎)에 겹친 요청·서버 두 대 계약 테스트, Mock PG 결제 조회 횟수 (같은 주문 동시 준비·같은 결제 동시 승인·취소·알림 반영과 승인 기록 겹침·다시 온 승인·서버 두 대 조회 테스트 실패)
- `ch07` — 7장 동시성 제어: 같은 결제를 바꾸는 기록은 결제 줄을 select … for update 로 잠금, 카드사 답을 기다리는 결제에 같은 결제 키는 202 확인 중, 같은 주문 동시 준비는 중복 거부를 잡아 다시 읽기, 같은 키 동시 취소는 잠금 뒤 5장 규칙으로, 주기 작업은 작업 잠금 표로 서버 한 대만, 비교 구현(잠그지 않음, 변경 번호 비교, 상태 조건 UPDATE, 409 그대로, 첫 요청 기다리기, 중복 잡지 않음, MySQL 이름 잠금, 나누지 않음, 결제마다 점유)
- `ch08-start` — 8장 출발 구조: 준비 요청에 상품·쿠폰·사용 포인트(고를 수 있는 값)를 받되 고객 금액을 믿고, 승인 뒤 재고·포인트를 잠그지 않고 읽어 줄이고 쿠폰을 쓴 것으로 바꾸며, 거절·취소에도 되돌리지 않음. Mock PG 카드사 쪽 만료·거절 답 붙잡기 (과판매·금액 조작·같은 쿠폰·되돌리기 테스트 실패)
- `ch08` — 8장 결제와 재고·포인트·쿠폰: 결제 금액은 서버가 상품가 합 − 쿠폰 − 포인트로 계산(다르면 400), 승인 요청 직전 인증 트랜잭션에서 잡고 모자라면 승인 요청 안 함(409), 재고·포인트는 조건부 UPDATE, 승인 실패·카드사 만료·전액 취소는 상태 전이와 같은 트랜잭션에서 한 번 되돌림(포인트 증감 이력), 쿠폰은 조건부 UPDATE, 비교 구현(고객 금액 믿기, 승인 뒤·준비 때 잡기, 잠그지 않음, select … for update, 변경 번호 + 다시 시도, 곳마다 따로 되돌리기, 이력 없이 잔액만, 읽고 확인한 뒤 저장, 쿠폰 사용 기록)
- `ch09-start` — 9장 출발 구조: 결제 결과(승인·승인 실패·취소 등)를 상태를 바꾼 그 트랜잭션 안에서 Kafka 로 바로 보내고(키 없이, 받았다는 답을 기다림, 실패하면 로그만), 주문 서비스(`order-service`)가 받아 주문 상태를 바꿈 (유실·유령 이벤트·순서 뒤바뀜·응답 지연 테스트 실패)
- `ch09` — 9장 Transactional Outbox와 Kafka: 결제 결과는 상태 전이와 같은 트랜잭션에서 `payment_outbox`에 남기고, 주기 폴링 릴레이가 번호 순으로 보내 받았다는 답이 오면 보낸 때를 남김, 릴레이는 작업 잠금으로 서버 한 대만, 메시지 키 = 결제 번호·acks=all·멱등 프로듀서, 보내지 못한 결제의 다음 이벤트는 기다림, 적어도 한 번(이벤트 고유 번호 = 남긴 결과 번호), 비교 구현(그 자리에서 바로 보냄, 커밋 뒤 보냄, 잠그지 않음, 한 줄씩 skip locked, 키 없이, 먼저 남기고 보냄)
- `ch10-start` — 10장 출발 구조: 재고를 재고 서비스(`inventory-service`)로 떼어 내고 8장 방식을 그대로 HTTP 호출로 바꿈 — 인증 트랜잭션 안에서 상품마다 재고를 줄이고, 승인 없이 끝나거나 전액 취소되면 그 자리에서 늘림(실패하면 로그만), 예약 기록 없음 (재고 서비스 답이 늦은 결제의 재고 유실·늦게 도착한 줄이기·멈춘 동안 되돌리기 유실·확정 테스트 실패)
- `ch10` — 10장 Saga와 보상 트랜잭션: 카드사 승인 요청 전에 재고 서비스에 동기 HTTP 로 예약(연결 300ms·읽기 1s), 모자라면 승인 없이 실패로 닫고 쿠폰·포인트 돌려줌, Saga 진행은 결제 서버가 결제 상태로 쥐고 예약·확정·해제를 정해 보냄(오케스트레이션), 재고 서비스는 결제 번호로 예약 줄(잡음 → 확정 / 해제, 해제가 먼저 오면 빈 해제 줄로 늦은 예약을 막고 같은 확정·해제는 한 번만), 예약 답을 받지 못하면 승인 요청 없이 닫고 해제, 확정·해제는 상태 전이 트랜잭션에서 `payment_outbox`에 남겨 9장 릴레이가 결제 번호 키로 보냄, 비교 구현(승인 먼저 + Kafka 차감 + 카드사 취소 보상, 코레오그래피, 수량만, 실패로만, 조회로 확인, 그 자리에서 HTTP)
  - 결정 커밋은 1·3·2·4·5 순서: 결정 2의 비교 구현(코레오그래피)이 결정 3의 예약 기록이 있어야 돌아서 결정 3을 먼저 커밋함(커밋 메시지의 결정 번호는 글의 순서)

## 모듈

- 루트: 결제 서버
- `mock-pg`: 응답 지연·거절·서버 오류를 제어 API(`/control/scenario`)로 바꿀 수 있는 가짜 PG. 4장부터 주문번호 결제 조회와 결제 취소, 5장부터 부분 취소와 멱등키(`Idempotency-Key`)도 받는다. 6장부터 결제 상태가 바뀌면 결제 서버로 웹훅(`PAYMENT_STATUS_CHANGED`)을 보내고 200 이 아니면 7번까지 다시 보낸다(순서 바꾸기·중복·위조·가맹점 관리 화면 취소는 `/control/webhook*`, `/control/console-cancels/{orderId}`). 7장부터 주문번호 결제 조회를 받은 횟수를 센다(`/control/inquiries/{orderId}`). 8장부터 승인 요청이 닿지 않은 결제를 카드사 쪽에서 만료시킬 수 있다(`/control/expiries/{orderId}`). 따로 띄우면 8090 포트, 결제 서버 테스트에서는 같은 JVM 안에서 임의 포트로 띄운다
- `order-service`(9장부터): 결제 이벤트 토픽(`payment-events`, 파티션 2개)을 읽어 받은 이벤트를 그대로 남기고 주문 상태를 마지막으로 받은 결제 결과대로 바꾸는 주문 서비스. 중복·순서·재시도는 가리지 않는다(11장). DB 는 결제 서버와 같은 MySQL 의 다른 스키마(`order_service`)를 다른 계정으로 쓴다. 결제 서버 테스트에서는 같은 JVM 안에서 띄운다
- `inventory-service`(10장부터): 결제 서버에서 떼어 낸 재고 서비스. 재고(`stock`)와 결제 번호로 남기는 예약(`stock_reservation`, 잡음 → 확정 / 해제). 결제 서버가 HTTP 로 예약하고(`POST /reservations`, 조회 `GET /reservations/{id}`), 확정·해제는 재고 명령 토픽(`stock-reservation-commands`, 키 = 결제 번호)으로 받는다. 같은 확정·해제가 다시 와도 예약 상태 전이로 한 번만 반영한다(일반 중복 가리기·재시도는 11장). DB 는 같은 MySQL 의 다른 스키마(`inventory_service`)를 다른 계정으로 쓴다. 따로 띄우면 8091 포트, 결제 서버 테스트에서는 같은 JVM 안에서 띄운다

9장부터 결제 서버는 Kafka 브로커(`spring.kafka.bootstrap-servers`, 기본 `localhost:9092`)에 결제 이벤트를 보낸다. 테스트는 시험 JVM 에 브로커(`apache/kafka:4.2.0`) 하나를 띄워 모든 테스트가 같이 쓴다.

장별 실행 환경과 테스트 결과는 `docs/chNN/`에, 2장 부하 테스트 스크립트와 결과는 `load-test/ch02/`에 있습니다.

- `./gradlew check`: 테스트 + 커버리지 검사
- `./gradlew knownFailingTest`: 계약을 지키지 못하는 비교 구현을 같은 테스트로 돌려 실패를 확인(빌드는 실패시키지 않음)
- `./gradlew reconciliationMeasurement`: 4장 결과 모름 보정을 구현마다 같은 결제 묶음으로 돌려 건수·상태·보정 횟수를 기록(`build/measurement/`)
- `./gradlew cancelMeasurement`: 5장 취소를 구현마다 같은 취소 묶음으로 돌려 카드사 취소 건수와 잔액 어긋남을 기록(`build/measurement/`)
- `./gradlew webhookMeasurement`: 6장 웹훅 처리를 구현마다 같은 알림 묶음으로 돌려 카드사와 어긋난 결제 수, 재전송 횟수, 맞춰지기까지 시간을 기록(`build/measurement/`)
- `./gradlew concurrencyMeasurement`: 7장 같은 결제에 동시 요청 N개를 보내 잠금 방식마다 응답 시간과 답을 세고, 서버 수마다 보정 주기의 카드사 조회 횟수를 기록(`build/measurement/`)
- `./gradlew stockMeasurement`: 8장 재고 100개 상품에 승인 요청 300건을 한꺼번에 보내 재고를 줄이는 방식마다 과판매 수·승인 수·응답 시간·처리량을 기록(`build/measurement/`)
- `./gradlew outboxMeasurement`: 9장 결제 60건을 승인·취소하는 동안 브로커를 4초 끊어 보내는 방식마다 유실·중복·순서 위반·도착 지연을 세고, 쌓인 결과 200건을 서버 두 대가 비우는 시간을 릴레이를 나누는 방식마다 기록(`build/measurement/`)
- `./gradlew sagaMeasurement`: 10장 재고 서비스를 늦추고 끊은 동안 방식마다 결제 응답 시간·넘겨 판 수량·돌아오지 않은 재고·원복 시간을 세고, 재고 예약 응답 시간을 기록(`build/measurement/`)

## 코드 규칙

- 계층: `presentation → application → domain ← infrastructure`, 업무 예외와 오류 코드는 `domain/error` 한 곳(재고 서비스도 같다)(domain 은 다른 계층을 참조하지 않음), `global/error`에는 HTTP 응답으로 바꾸는 전역 예외 처리기만 둔다
- setter 없음. 엔티티는 `protected` 기본 생성자 + private `@Builder` 생성자 + 정적 팩터리, 상태는 행위 이름 메서드로만 바꾼다
- `if (!…)` 대신 `Objects.isNull`/`nonNull`이나 이름 있는 도우미, `else` 대신 early return
- 주석은 한국어, 예외·로그 문장은 영어
- 테스트 이름(`@DisplayName`)은 도메인 언어로 쓰고, 테스트 하나에 규칙 하나
- 커버리지 90% 이상(`./gradlew check`)
