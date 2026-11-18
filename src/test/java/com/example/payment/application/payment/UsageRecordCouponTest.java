package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.coupon.comparison.UsageRecordCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(UsageRecordCouponRepository.class)
@TestPropertySource(properties = "spring.sql.init.schema-locations=classpath:schema.sql,classpath:coupon-usage.sql")
@DisplayName("쿠폰 한 장을 두 주문에 — 비교: 사용 기록에 두 번 올라갈 수 없게")
class UsageRecordCouponTest extends CouponContract {
}
