package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.payment.comparison.NoLockPaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** JPA @Version 비교는 시험용 결제를 앞세운 클래스 경로가 필요해 ./gradlew jpaVersionMeasurement 로 돈다(concurrencyMeasurement 뒤에 함께 돈다) */
@Tag("jpa-version-measurement")
@Import(NoLockPaymentRepository.class)
@TestPropertySource(properties = "spring.sql.init.schema-locations=classpath:schema.sql,classpath:versioned-payment.sql")
@DisplayName("잠금 방식 측정 — @Version 그대로(결제 줄이 바뀔 때만 비교), 어긋나면 그대로 오류")
class JpaVersionLockMeasurement extends LockMeasurement {

    @Override
    String name() {
        return "@Version 그대로, 어긋나면 그대로 오류";
    }
}
