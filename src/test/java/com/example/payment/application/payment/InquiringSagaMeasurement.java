package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.InquiringHoldings;
import org.springframework.context.annotation.Import;

@Import(InquiringHoldings.class)
class InquiringSagaMeasurement extends SagaMeasurement {

    @Override
    String name() {
        return "결정 4 비교: 답 없으면 조회로 확인";
    }
}
