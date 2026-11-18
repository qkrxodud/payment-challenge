package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.point.comparison.BalanceOnlyPointRepository;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.annotation.Import;

@Import(BalanceOnlyPointRepository.class)
@DisplayName("승인 없이 끝난 결제가 잡아 둔 재고·포인트·쿠폰 — 비교: 포인트 이력 없이 잔액만 고치기(되돌리는 때는 운영과 같음)")
class BalanceOnlyReleaseTest extends ReleaseContract {
}
