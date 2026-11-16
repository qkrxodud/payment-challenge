# 6장 실행 환경

- 머신: Apple M1 Max(10코어), 메모리 32GB, macOS 26.4.1 (Darwin 25.4.0)
- JDK: Azul Zulu 17.0.15 (Gradle toolchain 17)
- Gradle 9.8.2 (wrapper), Spring Boot 4.1.2, Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, HikariCP 7.0.2, Tomcat 11.0.27, Jackson 3.1.7
- MySQL Connector/J 9.7.0, Testcontainers 2.0.5, MySQL 8.4 (`mysql:8.4` 이미지)
- Docker 28.3.0

## 테스트

실행: `JAVA_HOME=<JDK 17> ./gradlew check` (Docker 필요, 커버리지 90% 미만이면 실패)

- `01-start-structure-test-result.txt`: 태그 `ch06-start`(웹훅을 받아 본문의 결제 상태를 허용 전이 표대로 그 자리에서 옮기는 엔드포인트)에서 6장 테스트(웹훅 계약 운영 구현, 웹훅 받기 API)를 돌린 결과
- `02-final-test-result.txt`: 태그 `ch06`에서 전체 테스트(`./gradlew clean check`)를 돌린 결과와 커버리지
- `03-comparison-known-failing-result.txt`: `./gradlew knownFailingTest`로 계약을 못 지키는 비교 구현을 돌린 결과(2~5장 비교 구현 포함)
- `04-webhook-measurement.txt`: `./gradlew webhookMeasurement`로 구현마다 같은 알림 묶음을 보낸 측정 결과
- `05-reconciliation-measurement-at-ch06.txt`: 4장 `./gradlew reconciliationMeasurement`를 6장 코드(4장 보정을 웹훅 해석에 맞춘 뒤)에서 다시 돌린 결과. 4장 시점 기록은 `docs/ch04/04-reconciliation-measurement.txt`

웹훅 계약(`WebhookContract`)은 실제 HTTP 로 Mock PG 가 결제 서버에 알림을 보낸다(서버는 임의 포트, 구현마다 받는 주소가 다르다).
결제 금액 15000원, 부분 취소 5000원, PG 읽기 시간 제한 1초, 늦은 답은 1.5초 뒤에 온다. 보정 대기 시간은 10초다.
알림을 200 으로 받지 못하면 Mock PG 는 100ms 간격으로 7번까지 다시 보낸다(토스페이먼츠는 1, 4, 16, 64, 256, 1024, 4096분 — 횟수만 같게 두고 간격은 줄였다).
알림의 답을 기다리는 시간은 토스페이먼츠와 같은 10초다.
받은 알림 처리(`PaymentWebhookProcessor`)는 시험이 직접 부르고, 주기 처리는 `payment.webhook.enabled=false` 로 끈다(`ScheduledWebhookProcessingTest`만 켠다).
"카드사와 같다"는 결제 상태가 Mock PG 상태와 짝이 맞고(DONE·PARTIAL_CANCELED ↔ 승인·매입, CANCELED ↔ 승인 취소·매입 취소, ABORTED ↔ 승인 실패)
우리 남은 금액(결제 금액 − 끝난 취소의 합)이 Mock PG 잔액과 같은지로 본다.
비교 구현(웹훅 받는 주소)은 `WebhookComparisonConfiguration` 에 모아 운영 구현 시험에도 함께 올린다. 주소가 달라 운영 구현의 동작에는 끼어들지 않는다.

## Mock PG

6장에서 더하거나 바꾼 것. 토스페이먼츠 개발자센터의 웹훅 가이드(`guides/v2/webhook`)와 웹훅 이벤트 레퍼런스(`reference/using-api/webhook-events`)에서 확인한 것만 흉내 낸다(2026-11-16 확인).

확인한 것:
- 결제 상태가 바뀌면(승인 DONE, 거절 ABORTED, 취소 PARTIAL_CANCELED·CANCELED) `PAYMENT_STATUS_CHANGED` 웹훅을 POST 로 보낸다.
  본문은 `eventType`, `createdAt`(`yyyy-MM-dd'T'HH:mm:ss.SSSSSS`), `data`(상태가 바뀐 Payment 객체 — Mock PG 의 결제 조회 응답과 같은 모양)이고 `eventId`는 없다.
- 10초 안에 200 으로 답하면 성공. 아니면 최대 7회 재전송(간격 설정값, 기본은 토스페이먼츠 간격). 7회를 다 쓰면 더 보내지 않는다.

문서에서 확인하지 못해 Mock PG 가 가정한 것:
- 알림 순서: 결제 알림의 순서 보장 문구가 없다(에스크로 이벤트에만 "순서는 보장되지 않는다"가 있다). Mock PG 는 상태가 바뀐 순서대로 바로 보내고,
  시험에서만 쌓아 두었다가 순서를 바꿔 보낸다(0.1초씩 띄워 보낸다).
- 재전송 본문은 처음 본문과 같다(`createdAt` 도 같다). "같은 상태를 다른 때에 다시"는 시험이 새 알림(새 `createdAt`)으로 만든다.
- `createdAt` 은 한국 시각이다(문서 형식에 시간대가 없다).
- 서명: 레퍼런스의 웹훅 헤더 목록에 `tosspayments-webhook-signature`가 있지만 어느 이벤트에 붙는지 적혀 있지 않아, 결제 상태 알림에 붙는지 분명하지 않다.
  Mock PG 는 서명과 `tosspayments-webhook-transmission-*` 헤더를 붙이지 않고, 결제 서버도 서명을 확인하지 않는다(위조 알림은 카드사 조회로 막는다).
- 매입(capture) 웹훅은 없다(문서에 없음). 위조 알림은 시험용 조작이다.

시험용 조작(테스트에서 같은 JVM 으로, 따로 띄우면 `/control/webhook*`, `/control/console-cancels/{orderId}`):
받을 주소, 쌓아 두기·순서 바꿔 보내기, 같은 본문 두 번, 늦게 보내기, 같은 상태 다시 보내기, 위조 알림, 가맹점 관리 화면 취소(카드사에서만 일어난 취소, 알림이 감),
승인을 기록하고 알린 뒤 승인 답 붙잡아 두기, 알림마다 받은 상태 코드와 걸린 시간.

## 부하 테스트

6장에는 처리량에 대한 주장이 없어 k6 를 돌리지 않았다. 알림 응답 시간·재전송·맞춰지기까지 시간은 `04-webhook-measurement.txt`에 있다.
