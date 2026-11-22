package com.example.payment.application.payment;

import com.example.payment.domain.error.exception.PaymentGatewayFailedException;
import com.example.payment.domain.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.reconciliation.Mismatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 2: 우리 기록이 카드사보다 뒤처진 결제(카드사 쪽이 더 나아감)만 대사가 맞춘다. 맞추는 길은 6장 웹훅과 같다 — 카드사에 결제를 다시 조회하고
 * 허용 전이 표로 갈 수 있는 방향이면 따라간다(PaymentWebhookRecorder.follow). 거래 내역의 값을 그대로 믿고 바꾸지 않는다.
 * 금액이 다르거나 한쪽에만 있거나 우리가 앞선 결제는 고치지 않고 운영자 확인으로 남긴다 — 우리 금액은 바꿀 수 없는 값이고(1장),
 * 따라가기는 뒤로 가지 않으며(6장), 한쪽에만 있는 결제는 어느 쪽이 맞는지 기록만으로 알 수 없다.
 * 조회에 답이 없으면 운영자 확인으로 남긴다 — 다음에 같은 날을 다시 대사하면 다시 맞춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowBehindResolver implements MismatchResolver {

    private final PaymentGateway paymentGateway;
    private final PaymentWebhookRecorder paymentWebhookRecorder;

    @Override
    public Mismatch resolve(Mismatch mismatch) {
        if (mismatch.isFollowable()) {
            return follow(mismatch);
        }
        return mismatch;
    }

    private Mismatch follow(Mismatch mismatch) {
        try {
            paymentWebhookRecorder.follow(mismatch.getOrderId(), paymentGateway.findPayment(mismatch.getOrderId()));
            return mismatch.followed();
        } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException unanswered) {
            log.warn("Could not look up a payment behind the gateway ({}). It is left for review.",
                    unanswered.getClass().getSimpleName());
            return mismatch;
        }
    }
}
