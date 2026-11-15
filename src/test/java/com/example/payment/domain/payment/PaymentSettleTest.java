package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("카드사에 확인한 결과로 결제 닫기")
class PaymentSettleTest {

    @Test
    @DisplayName("카드사에 승인이 남아 있으면 결과 모름 결제는 승인 상태가 된다")
    void approvedByCardCompany() {
        // given: 카드사 답을 받지 못한 결제
        Payment payment = unknownPayment();

        // when
        payment.settle(PaymentInquiry.APPROVED);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("카드사에 승인이 없으면 결과 모름 결제는 승인 실패로 닫힌다")
    void notApprovedByCardCompany() {
        // given: 카드사 답을 받지 못한 결제
        Payment payment = unknownPayment();

        // when
        payment.settle(PaymentInquiry.NOT_APPROVED);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    @Test
    @DisplayName("카드사도 아직 처리 중이면 결과 모름 그대로 둔다")
    void undecidedByCardCompany() {
        // given: 카드사 답을 받지 못한 결제
        Payment payment = unknownPayment();

        // when
        payment.settle(PaymentInquiry.UNDECIDED);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_UNKNOWN);
    }

    private static Payment unknownPayment() {
        Payment payment = Payment.create("order-20261115-0001", new BigDecimal("15000"));
        payment.authenticate("key-1");
        payment.markApprovalUnknown();
        return payment;
    }
}
