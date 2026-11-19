package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.AfterCommitPaymentEvents;
import org.springframework.context.annotation.Import;

@Import(AfterCommitPaymentEvents.class)
class AfterCommitDeliveryMeasurement extends EventDeliveryMeasurement {

    @Override
    String name() {
        return "커밋 뒤 보냄(키 없이)";
    }
}
