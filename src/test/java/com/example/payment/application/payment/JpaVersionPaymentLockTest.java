package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.payment.comparison.NoLockPaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 결제에 JPA @Version 을 둔 비교. 운영 결제는 그대로 두고, 빌드가 운영 Payment.java 에 변경 번호 칸만 더해 만든 시험용 결제를
 * 클래스 경로 맨 앞에 올려 돈다(./gradlew jpaVersionTest, knownFailingTest 를 부르면 함께 돈다). 변경 번호 칸은 이 시험 DB 에만 더한다.
 * 결제를 바꾸려고 읽을 때 잠그지 않고 Hibernate 의 버전 비교(update … where id = ? and version = ?, 결제 줄이 바뀔 때만)에 맡긴다.
 * 버전이 어긋나면 Hibernate 가 낸 오류를 그대로 둔다(전역 오류 처리기는 내부 오류 500 으로 답한다) — 운영 결제에 @Version 만 붙였을 때와 같다.
 * 이 비교 구현은 계약 일부를 지키지 못한다(기본 테스트에서 뺀다)
 */
@Tag("jpa-version")
@Import(NoLockPaymentRepository.class)
@TestPropertySource(properties = {
        "spring.sql.init.schema-locations=classpath:schema.sql,classpath:versioned-payment.sql"
})
@DisplayName("같은 결제를 바꾸는 동시 기록 — 비교: @Version 그대로(결제 줄이 바뀔 때만 비교), 어긋나면 그대로 오류")
class JpaVersionPaymentLockTest extends PaymentLockContract {
}
