# 5장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2, Tomcat 11.0.27, Jackson 3.1.7
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지)
- Docker 28.3.0

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch05-start`(받은 금액으로 카드사에 취소를 보내고 결제 상태만 바꾸는 취소 API)에서 5장 테스트(취소 계약 운영 구현, 결제 취소 API)를 돌린 결과
- `02-final-test-result.txt`: 태그 `ch05`에서 전체 테스트(`./gradlew clean check`)를 돌린 결과와 커버리지
- `03-comparison-known-failing-result.txt`: `./gradlew knownFailingTest`로 계약을 못 지키는 비교 구현을 돌린 결과(2·3·4장 비교 구현 포함)
- `04-cancel-measurement.txt`: `./gradlew cancelMeasurement`로 구현마다 같은 취소 묶음을 보내고 보정한 측정 결과

취소 계약(`CancelContract`)은 결제 금액 15000원, 부분 취소 5000원, PG 읽기 시간 제한 1초, 늦은 취소는 카드사가 1.5초 뒤에 답하게 둔다.
보정은 카드사가 취소 처리를 마칠 때까지 기다린 뒤 한 번 돌리고, 보정에 넘기는 시각은 보정 대기 시간(`payment.reconciliation.wait-after-request=10s`)이 지난 때로 준다.
"카드사와 같다"는 우리가 답한(또는 기록한) 남은 금액이 Mock PG 의 잔액(`balanceAmount`)과 같은지로 본다.
카드사 취소 뒤 결과 기록 실패는 2·4장과 같이 MySQL 트리거(취소 완료로 바꾸는 저장을 막음)로 만든다.
매입 API 는 아직 없어서, 매입된 결제의 취소 테스트는 승인된 결제의 상태를 `CAPTURED`로 바꿔 두고 시작한다.

## Mock PG

5장에서 더하거나 바꾼 것. 토스페이먼츠 개발자센터의 코어 API(결제 취소, Payment 객체), 인증 및 기타 헤더(멱등키), 에러 코드 문서에서 확인한 것만 흉내 낸다.

- `POST /v1/payments/{paymentKey}/cancel`: 본문에 `cancelAmount`(없으면 남은 금액 전부)를 받아 부분 취소한다. 응답은 `balanceAmount`와
  `cancels`(`transactionKey`, `cancelAmount`, `cancelReason`, `canceledAt`, `cancelStatus=DONE`)를 담은 Payment 다.
  잔액이 0이 되면 `CANCELED`, 남으면 `PARTIAL_CANCELED`. 잔액을 넘거나 0원 이하면 403 `NOT_CANCELABLE_AMOUNT`, 이미 전액 취소면 400 `ALREADY_CANCELED_PAYMENT`,
  승인 실패 결제면 403 `NOT_CANCELABLE_PAYMENT`.
- `Idempotency-Key` 헤더: 같은 키(결제마다)로 다시 오면 다시 처리하지 않고 첫 응답을 그대로 준다. 같은 키의 앞 요청이 아직 처리 중이면 409 `IDEMPOTENT_REQUEST_PROCESSING`.
  - 가정(문서에서 확인하지 못함): 같은 키에 다른 본문이 오면 본문을 보지 않고 첫 응답을 준다. 키 유효 기간(토스페이먼츠 15일)은 흉내 내지 않는다(Mock PG 가 떠 있는 동안 기억).
  - 가정: `NOT_CANCELABLE_AMOUNT`를 "잔액을 넘는 금액"에 쓴다(문서 설명은 "취소 할 수 없는 금액 입니다.").
- `GET /v1/payments/orders/{orderId}`: 응답에 `balanceAmount`, `cancels`를 더했다.
- 취소는 시나리오의 `followUpDelay`만큼 기다린 뒤 처리하므로, 결제 서버가 기다리기를 그만둬도 취소는 남는다.
- 제어용(테스트에서 같은 JVM으로 부름): 취소 요청을 받은 횟수(같은 키로 다시 와 첫 응답을 돌려준 요청 포함), 실제 취소 횟수, 잔액, 카드사 쪽에서 직접 전액 취소.

## 부하 테스트

5장에는 처리량·지연·경합에 대한 주장이 없어 k6 를 돌리지 않았다. 구현별 취소 건수·잔액 어긋남은 `04-cancel-measurement.txt`에 있다.
