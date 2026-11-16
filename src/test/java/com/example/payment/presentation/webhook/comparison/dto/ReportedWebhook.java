package com.example.payment.presentation.webhook.comparison.dto;

import com.example.payment.domain.payment.GatewayCancel;
import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.GatewayStatus;
import java.util.List;
import java.util.Objects;

// 비교 구현이 읽는 웹훅 본문 전체: 상태와 취소 이력까지 본문에 적힌 그대로
public record ReportedWebhook(String eventType, String createdAt, ReportedPayment data) {

    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";

    public boolean isPaymentStatusChange() {
        return PAYMENT_STATUS_CHANGED.equals(eventType) && Objects.nonNull(data);
    }

    public String orderId() {
        return data.orderId();
    }

    // 본문에 적힌 결제를 카드사에서 읽은 결제처럼 옮긴다. 취소는 끝난(DONE) 것만
    public GatewayPayment reported() {
        List<GatewayCancel> cancels = Objects.requireNonNullElse(data.cancels(), List.<ReportedCancel>of()).stream()
                .filter(cancel -> "DONE".equals(cancel.cancelStatus()))
                .map(cancel -> GatewayCancel.of(cancel.transactionKey(), cancel.cancelAmount(), cancel.cancelReason()))
                .toList();
        return GatewayPayment.of(GatewayStatus.of(data.status()), cancels);
    }
}
