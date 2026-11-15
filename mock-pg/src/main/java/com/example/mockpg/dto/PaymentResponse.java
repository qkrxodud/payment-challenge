package com.example.mockpg.dto;

import com.example.mockpg.PaymentRecord;
import java.math.BigDecimal;

/** 결제 조회·취소의 응답. 토스페이먼츠 Payment 객체에서 결제 서버가 읽는 필드만 둔다 */
public record PaymentResponse(String paymentKey, String orderId, String status, BigDecimal totalAmount) {

    public static PaymentResponse from(PaymentRecord record) {
        return new PaymentResponse(record.getPaymentKey(), record.getOrderId(), record.getState().name(), record.getTotalAmount());
    }
}
