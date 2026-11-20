package com.example.payment.application.payment;

import com.example.payment.domain.outbox.OutboxType;
import com.example.payment.domain.outbox.PaymentOutbox;
import com.example.payment.domain.outbox.PaymentOutboxRepository;
import com.example.payment.domain.payment.Payment;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 10장 결정 5: 재고 서비스에 보낼 예약 확정·해제를 결제 상태를 바꾼 그 트랜잭션에서 payment_outbox 에 남긴다(9장 결제 결과와 같은 표).
 * 상태가 커밋되면 명령도 반드시 남고, 커밋이 실패하면 함께 남지 않는다. 보내는 일은 9장 릴레이가 결제 번호 키로 한다 —
 * 재고 서비스가 멈춰 있어도 결제 기록은 기다리지 않고, 돌아오면 남은 명령이 간다.
 */
@Component
@RequiredArgsConstructor
public class StockCommands {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final Clock clock;

    public void confirm(Payment payment) {
        paymentOutboxRepository.save(PaymentOutbox.stockCommand(payment, OutboxType.STOCK_CONFIRM, clock.instant()));
    }

    public void release(Payment payment) {
        paymentOutboxRepository.save(PaymentOutbox.stockCommand(payment, OutboxType.STOCK_RELEASE, clock.instant()));
    }
}
