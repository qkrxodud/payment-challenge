package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.PerPaymentInquiryReconciler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.ApplicationContext;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("하루치 대사 — 비교(결정 1): 결제마다 카드사 단건 조회")
class PerPaymentInquiryReconciliationTest extends DailyReconciliationContract {

    @Override
    void reconcile(LocalDate day) {
        reconcileOn(thisServer, day);
    }

    @Override
    void reconcileOn(ApplicationContext server, LocalDate day) {
        PerPaymentInquiryReconciler.on(server).reconcile(day);
    }

    @Override
    List<ReconciliationLine> resultsOf(LocalDate day) {
        return PaymentDailyReconciliationTest.resultsIn(jdbcTemplate, day);
    }
}
