package com.example.mockpg;

import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 6장: 결제 서버를 거치지 않고 카드사 쪽에서 일어나는 취소(가맹점 관리 화면에서 취소한 경우). 취소하면 웹훅으로 알린다.
 * 금액이 없으면 남은 금액을 모두 취소한다.
 */
@Component
@RequiredArgsConstructor
public class MerchantConsole {

    private final ScenarioStore scenarioStore;
    private final WebhookSender webhookSender;

    public CancelAttempt cancel(String orderId, BigDecimal amount, String reason) {
        CancelAttempt attempt = scenarioStore.cancel(orderId, amount, reason);
        // 주문을 찾지 못하면 처리 결과가 없다
        if (Objects.nonNull(attempt.getOutcome()) && attempt.getOutcome().isCanceled()) {
            webhookSender.statusChanged(attempt.getRecord());
        }
        return attempt;
    }
}
