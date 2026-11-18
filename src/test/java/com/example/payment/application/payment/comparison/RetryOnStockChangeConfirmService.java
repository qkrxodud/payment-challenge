package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.infrastructure.persistence.stock.comparison.StockChangedException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;

/**
 * 비교 구현(8장 결정 3, 변경 번호 + 다시 시도): 재고 변경 번호가 어긋나면 승인을 처음부터 다시 요청한다. 인증 트랜잭션은 되돌려져
 * 결제는 준비 상태이고 카드사에는 아직 보내지 않았다. 다시 해도 어긋나기를 MAX_ATTEMPTS 번 하면 그 문제를 그대로 알린다(내부 오류).
 */
@RequiredArgsConstructor
public class RetryOnStockChangeConfirmService {

    public static final int MAX_ATTEMPTS = 30;

    private final PaymentConfirmService paymentConfirmService;
    // 측정용: 변경 번호가 어긋나 다시 시도한 횟수(시험 도구라 상태를 둔다)
    private final AtomicInteger retries = new AtomicInteger();

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        StockChangedException lastChange = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return paymentConfirmService.confirm(command);
            } catch (StockChangedException changed) {
                retries.incrementAndGet();
                lastChange = changed;
            }
        }
        throw lastChange;
    }

    public int takeRetries() {
        return retries.getAndSet(0);
    }
}
