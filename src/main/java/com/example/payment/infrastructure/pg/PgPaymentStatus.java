package com.example.payment.infrastructure.pg;

import com.example.payment.domain.payment.PaymentInquiry;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 카드사 결제 조회 응답의 status 값(토스페이먼츠 Payment 객체)과 우리가 읽는 뜻.
 * 부분 취소는 승인이 남은 결제다. 목록에 없는 값은 카드사도 아직 처리 중인 것으로 보고 결제를 그대로 둔다.
 */
@Getter
@RequiredArgsConstructor
enum PgPaymentStatus {
    READY(PaymentInquiry.UNDECIDED),
    IN_PROGRESS(PaymentInquiry.UNDECIDED),
    WAITING_FOR_DEPOSIT(PaymentInquiry.UNDECIDED),
    DONE(PaymentInquiry.APPROVED),
    PARTIAL_CANCELED(PaymentInquiry.APPROVED),
    CANCELED(PaymentInquiry.NOT_APPROVED),
    ABORTED(PaymentInquiry.NOT_APPROVED),
    EXPIRED(PaymentInquiry.NOT_APPROVED);

    private final PaymentInquiry inquiry;

    static PaymentInquiry inquiryOf(String status) {
        return Arrays.stream(values())
                .filter(known -> known.name().equals(status))
                .findFirst()
                .map(PgPaymentStatus::getInquiry)
                .orElse(PaymentInquiry.UNDECIDED);
    }
}
