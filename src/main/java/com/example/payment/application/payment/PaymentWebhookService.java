package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ReceiveWebhookCommand;
import com.example.payment.domain.webhook.PaymentWebhook;
import com.example.payment.domain.webhook.PaymentWebhookRepository;
import java.time.Clock;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드사 웹훅 받기(6장). 결제 상태 알림은 "이 결제를 다시 보라"는 신호로만 쓴다(결정 1).
 * 받은 알림은 그 자리에서 남기기만 하고 바로 200 으로 답한다(결정 4). 카드사 조회와 결제 맞추기는 처리기(PaymentWebhookProcessor)가
 * 주기마다 한다 — 토스페이먼츠는 10초 안에 200 을 받아야 성공으로 보는데, 카드사 조회(읽기 제한 5초)와 DB 기록을 요청 안에서 하면
 * 그 안에 못 답할 수 있고, 조회에 실패해 오류로 답하면 카드사가 같은 알림을 다시 보내기 때문이다.
 * 결제 상태 알림이 아니거나 주문번호가 없는 알림은 맞출 결제가 없어 남기지 않고 받았다고만 답한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentWebhookRepository paymentWebhookRepository;
    private final Clock clock;

    @Transactional
    public void receive(ReceiveWebhookCommand command) {
        if (isNothingToFollow(command)) {
            log.info("A webhook with nothing to follow was received and left as is.");
            return;
        }
        paymentWebhookRepository.save(PaymentWebhook.received(command.orderId(), command.eventType(),
                command.reportedStatus(), command.reportedCreatedAt(), clock.instant()));
    }

    private static boolean isNothingToFollow(ReceiveWebhookCommand command) {
        if (command.isPaymentStatusChange()) {
            return Objects.isNull(command.orderId());
        }
        return true;
    }
}
