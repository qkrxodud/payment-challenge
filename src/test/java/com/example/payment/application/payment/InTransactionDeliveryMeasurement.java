package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.InTransactionPaymentEvents;
import org.springframework.context.annotation.Import;

@Import(InTransactionPaymentEvents.class)
class InTransactionDeliveryMeasurement extends EventDeliveryMeasurement {

    @Override
    String name() {
        return "상태를 바꾼 자리에서 바로 보냄(출발 구조, 키 없이)";
    }
}
