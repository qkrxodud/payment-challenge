package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.ChoreographyHoldings;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(ChoreographyHoldings.class)
@TestPropertySource(properties = {
        "test.inventory.reads-payment-events=true",
        "test.inventory.components=com.example.payment.application.payment.comparison.PaymentEventReactionListener"
})
class ChoreographySagaMeasurement extends SagaMeasurement {

    @Override
    String name() {
        return "결정 2 비교: 코레오그래피";
    }
}
