package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NoReleaseOnTimeoutRecorder;
import org.springframework.context.annotation.Import;

@Import(NoReleaseOnTimeoutRecorder.class)
class NoReleaseOnTimeoutSagaMeasurement extends SagaMeasurement {

    @Override
    String name() {
        return "결정 4 비교: 답 없으면 실패로만(해제 안 보냄)";
    }
}
