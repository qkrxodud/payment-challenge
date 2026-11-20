package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.DirectHttpHoldings;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(DirectHttpHoldings.class)
@TestPropertySource(properties = "test.inventory.components=com.example.inventorycomparison.ReservationCommandController")
class DirectHttpSagaMeasurement extends SagaMeasurement {

    @Override
    String name() {
        return "결정 5 비교: 그 자리에서 HTTP 로 확정·해제";
    }
}
