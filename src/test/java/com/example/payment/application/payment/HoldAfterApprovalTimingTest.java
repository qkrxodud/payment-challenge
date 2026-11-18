package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.DeferredHoldRecorder;
import com.example.payment.application.payment.comparison.DeferredHoldings;
import com.example.payment.application.payment.comparison.HoldAfterApprovalConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import({DeferredHoldings.class, DeferredHoldRecorder.class, HoldAfterApprovalConfirmService.class})
@DisplayName("재고를 줄이는 때 — 비교: 카드사 승인 뒤(모자라면 승인 취소)")
class HoldAfterApprovalTimingTest extends HoldTimingContract {

    @Autowired
    HoldAfterApprovalConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }
}
