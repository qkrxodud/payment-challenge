package com.example.payment.application.payment;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("운영 지표와 정리 — 비교(결정 5): 지표 없음")
class NoMetricsOperationalSignalsTest extends PaymentOperationalSignalsTest {

    // 11장까지처럼 실패 모음·밀림·대사 어긋남을 바깥에 내지 않는다(정리는 운영과 같다)
    @Override
    Optional<Double> paymentMeter(String name) {
        return Optional.empty();
    }

    @Override
    Optional<Double> orderMeter(String name) {
        return Optional.empty();
    }

    @Override
    Optional<Double> inventoryMeter(String name) {
        return Optional.empty();
    }
}
