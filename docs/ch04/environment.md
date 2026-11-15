# 4장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2, Tomcat 11.0.27, Jackson 3.1.7
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지)
- Docker 28.3.0

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch04-start`(카드사 답을 받지 못한 결제를 다루지 않는 승인 API)에서 4장 테스트(결과 모름 보정 계약, 전이 규칙 세 구현, 결제 금액과 승인 요청 확인, 결제 API)를 돌린 결과
- `02-final-test-result.txt`: 태그 `ch04`에서 전체 테스트(`./gradlew clean check`)를 돌린 결과와 커버리지
- `03-comparison-known-failing-result.txt`: `./gradlew knownFailingTest`로 계약을 못 지키는 비교 구현을 돌린 결과(2장·3장 비교 구현 포함)
- `04-reconciliation-measurement.txt`: `./gradlew reconciliationMeasurement`로 구현마다 같은 결제 묶음을 보정한 측정 결과

보정 계약(`ReconciliationContract`)은 PG 읽기 시간 제한 1초, 카드사 응답 1.5초 뒤로 둔다. 테스트는 카드사가 처리를 마칠 때까지 기다린 뒤
보정을 한 번 돌리고, 보정에 넘기는 시각은 보정 대기 시간(`payment.reconciliation.wait-after-request=10s`)이 지난 때로 준다.
"어긋나지 않는다"는 카드사에 남은 최종 상태와 결제 상태가 짝이 맞는지로 본다: 카드사 승인(DONE)이면 승인, 취소·거절·기록 없음이면 승인 실패.

테스트 태스크는 `payment.reconciliation.enabled=false`로 주기 보정을 끄고 보정을 직접 부른다. 주기 보정은 `ScheduledReconciliationTest` 하나만 켜서
(0.2초마다, 대기 시간 2초) 확인한다.

카드사 승인 뒤 결과 기록 실패는 2장과 같이 MySQL 트리거(승인 상태로 바꾸는 저장을 막음)로 만든다.

## Mock PG

4장에서 더한 API. 경로와 상태·오류 코드는 토스페이먼츠 API 문서(코어 API, 에러 코드)에서 확인한 것만 쓴다.

- `GET /v1/payments/orders/{orderId}`: 주문번호로 결제 조회. 승인이면 `DONE`, 거절이면 `ABORTED`, 취소되면 `CANCELED`, 기록이 없으면 404 `NOT_FOUND_PAYMENT`.
  응답 필드는 `paymentKey`, `orderId`, `status`, `totalAmount`만 둔다.
  토스페이먼츠 문서는 이 API를 "승인된 결제를 orderId로 조회"로 설명한다. 거절(`ABORTED`)도 조회로 돌려주는 것은 Mock PG의 가정이다.
- `POST /v1/payments/{paymentKey}/cancel` (본문 `cancelReason`): 승인된 결제를 취소. 이미 취소면 400 `ALREADY_CANCELED_PAYMENT`,
  승인되지 않은 결제면 403 `NOT_CANCELABLE_PAYMENT`, 없으면 404 `NOT_FOUND_PAYMENT`.
- 제어 API `PUT /control/scenario`에 `followUpDelayMillis`(조회·취소 응답 지연)를 더했고, 결과 `LOST`(기다리게 한 뒤 아무것도 기록하지 않고 500)를 더했다.
  `GET /control/cancels/{orderId}`로 취소 횟수를 본다.

## 부하 테스트

4장에는 처리량·지연·경합에 대한 주장이 없어 k6 를 돌리지 않았다. 구현별 건수·상태·보정 횟수는 `04-reconciliation-measurement.txt`에 있다.
