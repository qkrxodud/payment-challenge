# 결제 시스템 챌린지

단일 서버 Spring Boot 결제에서 출발해, 장마다 현재 구조의 한계를 테스트로 재현하고 해결하면서 구조를 키워 나가는 연재의 코드입니다.

- `ch01-start` — 1장 출발 구조: 상태를 검사하지 않는 변경 메서드 (전이 테스트 실패)
- `ch01` — 1장 결제 상태 머신: 허용 전이 표, 비교 구현(State 패턴, Spring Statemachine), 상태 컬럼 저장 방식

장별 실행 환경과 테스트 결과는 `docs/chNN/`에 있습니다.

## 코드 규칙

- 계층: `presentation → application → domain ← infrastructure`, 예외와 오류 코드는 `global/error` 한 곳(`global`은 다른 계층을 참조하지 않음)
- setter 없음. 엔티티는 `protected` 기본 생성자 + private `@Builder` 생성자 + 정적 팩터리, 상태는 행위 이름 메서드로만 바꾼다
- `if (!…)` 대신 `Objects.isNull`/`nonNull`이나 이름 있는 도우미, `else` 대신 early return
- 주석은 한국어, 예외·로그 문장은 영어
- 테스트 이름(`@DisplayName`)은 도메인 언어로 쓰고, 테스트 하나에 규칙 하나
- 커버리지 90% 이상(`./gradlew check`)
