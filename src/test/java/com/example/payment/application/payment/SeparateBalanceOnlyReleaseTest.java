package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.SeparateReleaseHoldings;
import com.example.payment.infrastructure.persistence.point.comparison.BalanceOnlyPointRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import({SeparateReleaseHoldings.class, BalanceOnlyPointRepository.class})
@DisplayName("승인 없이 끝난 결제가 잡아 둔 재고·포인트·쿠폰 — 비교: 곳마다 따로 되돌리기 + 포인트 이력 없이 잔액만")
class SeparateBalanceOnlyReleaseTest extends ReleaseContract {
}
