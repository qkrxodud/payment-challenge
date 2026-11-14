package com.example.payment.presentation.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.TestPropertySource;

/**
 * 비교: 트랜잭션은 나눈 채로 Open Session In View 만 다시 켠다(Spring Boot 기본값).
 * 이 설정은 계약을 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다
 */
@Tag("known-failing")
@TestPropertySource(properties = "spring.jpa.open-in-view=true")
@DisplayName("승인 대기 중 조회 — 비교: Open Session In View 켬")
class OpenSessionInViewApiTest extends PendingApprovalApiTest {
}
