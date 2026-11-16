package com.example.mockpg.dto;

import com.example.mockpg.PaymentRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 결제 조회·취소의 응답. 토스페이먼츠 Payment 객체에서 결제 서버가 읽는 필드만 둔다.
 * 5장부터 취소할 수 있는 금액(balanceAmount)과 취소 이력(cancels)을 담는다
 */
public record PaymentResponse(String paymentKey, String orderId, String status, BigDecimal totalAmount,
                              BigDecimal balanceAmount, List<CancelResponse> cancels) {

    public static PaymentResponse from(PaymentRecord record) {
        return new PaymentResponse(record.getPaymentKey(), record.getOrderId(), record.getState().name(),
                record.getTotalAmount(), record.getBalanceAmount(),
                record.getCancels().stream().map(CancelResponse::from).toList());
    }

    // 6장: 위조 알림에 담을 결제. 취소 상태면 결제 금액 전부를 취소한 이력을 지어 넣는다
    public static PaymentResponse forged(String orderId, String status, BigDecimal amount) {
        if ("CANCELED".equals(status)) {
            CancelResponse forgedCancel = new CancelResponse("tx-forged-" + UUID.randomUUID(), amount, "Forged",
                    OffsetDateTime.now().toString(), "DONE");
            return new PaymentResponse("forged-" + orderId, orderId, status, amount, BigDecimal.ZERO, List.of(forgedCancel));
        }
        return new PaymentResponse("forged-" + orderId, orderId, status, amount, amount, List.of());
    }
}
