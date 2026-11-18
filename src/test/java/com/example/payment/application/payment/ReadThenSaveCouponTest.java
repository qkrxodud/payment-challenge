package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.coupon.comparison.ReadThenSaveCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(ReadThenSaveCouponRepository.class)
@DisplayName("쿠폰 한 장을 두 주문에 — 비교: 읽고 확인한 뒤 저장(결정 4까지)")
class ReadThenSaveCouponTest extends CouponContract {
}
