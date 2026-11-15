# 결제 시스템 챌린지

단일 서버 Spring Boot 결제에서 출발해, 장마다 현재 구조의 한계를 테스트로 재현하고 해결하면서 구조를 키워 나가는 연재의 코드입니다.

## 장별 코드

각 장을 마친 시점의 코드 전체를 장별 브랜치에 그대로 둡니다. 브랜치를 고르면 그 장의 코드만 보이고, 그 상태로 바로 빌드됩니다.

- [`chapter-01`](https://github.com/qkrxodud/payment-challenge/tree/chapter-01) — 1장 결제 기초: 인증, 승인, 매입, 취소와 상태 머신
- [`chapter-02`](https://github.com/qkrxodud/payment-challenge/tree/chapter-02) — 2장 가장 단순한 결제 구현과 트랜잭션 경계
- [`chapter-03`](https://github.com/qkrxodud/payment-challenge/tree/chapter-03) — 3장 금액 검증과 멱등성
- [`chapter-04`](https://github.com/qkrxodud/payment-challenge/tree/chapter-04) — 4장 응답을 못 받았을 때의 처리
- [`chapter-05`](https://github.com/qkrxodud/payment-challenge/tree/chapter-05) — 5장 취소와 부분 취소

`main`에는 가장 최근 장까지의 코드가 있습니다. 각 장의 출발 구조(문제를 재현하는 코드)와 완성 코드는 아래 태그로도 볼 수 있습니다.

- `ch01-start` — 1장 출발 구조: 상태를 검사하지 않는 변경 메서드 (전이 테스트 실패)
- `ch01` — 1장 결제 상태 머신: 허용 전이 표, 비교 구현(State 패턴, Spring Statemachine), 상태 컬럼 저장 방식
- `ch02-start` — 2장 출발 구조: 인증 기록·PG 승인 호출·결과 기록을 트랜잭션 하나로 묶은 승인 API (승인 테스트 실패)
- `ch02` — 2장 트랜잭션 경계: PG 호출을 트랜잭션 밖으로, 거절도 기록, PG 시간 제한, Open Session In View 끄기, 비교 구현
- `ch03-start` — 3장 출발 구조: 고객이 보낸 금액을 그대로 PG에 보내는 승인 API (금액 변조·재요청·재준비 테스트 실패)
- `ch03` — 3장 금액 검증과 멱등성: 준비 금액과 값 비교, 결제 키로 다시 온 승인 요청 가리기, 같은 주문 재준비, 비교 구현
- `ch04-start` — 4장 출발 구조: 카드사 답을 받지 못한 결제를 다루지 않는 승인 API, Mock PG 결제 조회·취소 (결과 모름 보정·전이·API 테스트 실패)
- `ch04` — 4장 응답을 못 받았을 때의 처리: 결과 모름 상태, 카드사 조회 보정, 주기 보정과 대기 시간, 기록하지 못한 인증 상태 보정, 다시 온 요청에 202, 비교 구현(망취소, 아무것도 하지 않음, 요청 안에서 바로 조회, 기록 재시도)
- `ch05-start` — 5장 출발 구조: 받은 금액으로 카드사에 취소를 보내고 결제 상태만 바꾸는 취소 API, Mock PG 부분 취소·취소 이력·잔액·멱등키 (부분 취소·같은 요청 가리기·결과 모름 테스트 실패)
- `ch05` — 5장 취소와 부분 취소: 취소마다 한 줄(`payment_cancel`), 남은 금액으로 받고 0원이면 취소 상태, `Idempotency-Key` 필수와 같은 키 재요청·다른 내용 422, 취소 요청 먼저 기록과 결과 모름 보정, 카드사에도 멱등키 전달, 비교 구현(자연 키, 지금 상태로 답함, 카드사 먼저 부르고 기록, 카드사에 멱등키 안 넘김)

## 모듈

- 루트: 결제 서버
- `mock-pg`: 응답 지연·거절·서버 오류를 제어 API(`/control/scenario`)로 바꿀 수 있는 가짜 PG. 4장부터 주문번호 결제 조회와 결제 취소, 5장부터 부분 취소와 멱등키(`Idempotency-Key`)도 받는다. 따로 띄우면 8090 포트, 결제 서버 테스트에서는 같은 JVM 안에서 임의 포트로 띄운다

장별 실행 환경과 테스트 결과는 `docs/chNN/`에, 부하 테스트 스크립트와 결과는 `load-test/chNN/`에 있습니다.

- `./gradlew check`: 테스트 + 커버리지 검사
- `./gradlew knownFailingTest`: 계약을 지키지 못하는 비교 구현을 같은 테스트로 돌려 실패를 확인(빌드는 실패시키지 않음)
- `./gradlew reconciliationMeasurement`: 4장 결과 모름 보정을 구현마다 같은 결제 묶음으로 돌려 건수·상태·보정 횟수를 기록(`build/measurement/`)
- `./gradlew cancelMeasurement`: 5장 취소를 구현마다 같은 취소 묶음으로 돌려 카드사 취소 건수와 잔액 어긋남을 기록(`build/measurement/`)

## 코드 규칙

- 계층: `presentation → application → domain ← infrastructure`, 예외와 오류 코드는 `global/error` 한 곳(`global`은 다른 계층을 참조하지 않음)
- setter 없음. 엔티티는 `protected` 기본 생성자 + private `@Builder` 생성자 + 정적 팩터리, 상태는 행위 이름 메서드로만 바꾼다
- `if (!…)` 대신 `Objects.isNull`/`nonNull`이나 이름 있는 도우미, `else` 대신 early return
- 주석은 한국어, 예외·로그 문장은 영어
- 테스트 이름(`@DisplayName`)은 도메인 언어로 쓰고, 테스트 하나에 규칙 하나
- 커버리지 90% 이상(`./gradlew check`)
