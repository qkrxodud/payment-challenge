package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentConfirmService;
import com.example.payment.application.payment.PaymentQueryService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.config.PgProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

/**
 * 비교 구현(7장 결정 2): 첫 요청이 끝날 때까지 기다렸다가 같은 결과로 답함. 카드사 답을 기다리는(인증 상태) 결제에 같은 결제 키로
 * 승인이 다시 오면, 결제가 인증 상태를 벗어날 때까지 0.05초마다 다시 읽고 그 결과로 답한다. 첫 요청이 카드사를 기다릴 수 있는 시간
 * (PG 연결 + 읽기 시간 제한)이 지나도 인증 상태면 그 상태 그대로 답한다
 */
@RequiredArgsConstructor
public class WaitForFirstConfirmService {

    private static final long POLL_MILLIS = 50;

    private final PaymentConfirmService paymentConfirmService;
    private final PaymentQueryService paymentQueryService;
    private final PgProperties pgProperties;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        PaymentResult result = paymentConfirmService.confirm(command);
        Instant deadline = Instant.now().plus(pgProperties.getConnectTimeout()).plus(pgProperties.getReadTimeout());
        while (result.status() == PaymentStatus.AUTHENTICATED && Instant.now().isBefore(deadline)) {
            pause();
            result = paymentQueryService.find(command.orderId());
        }
        return result;
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
