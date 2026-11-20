package com.example.payment.application.payment;

import static org.awaitility.Awaitility.await;

import com.example.payment.application.payment.comparison.ApproveFirstConfirmService;
import com.example.payment.application.payment.comparison.ApproveFirstHoldings;
import com.example.payment.application.payment.comparison.ApproveFirstReplyListener;
import com.example.payment.application.payment.comparison.StockReplies;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.PaymentStatus;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다.
 * 결제 응답은 재고 서비스를 기다리지 않지만, 시험은 승인된 결제마다 재고 서비스의 답(과 보상 취소)이 처리될 때까지 기다린 뒤 다음으로 간다 —
 * 답이 오기 전에 취소·재고 읽기가 끼어드는 경우는 이 시험에서 고정하지 않는다.
 */
@Tag("known-failing")
@Import({ApproveFirstHoldings.class, ApproveFirstConfirmService.class, ApproveFirstReplyListener.class})
@TestPropertySource(properties = "test.inventory.components="
        + "com.example.payment.application.payment.comparison.ApproveFirstStockListener")
@DisplayName("결제와 나눈 재고를 함께 끝내기 — 비교(결정 1): 승인 먼저, Kafka 로 재고 차감, 모자라면 카드사 취소로 보상")
class ApproveFirstSagaTest extends SagaContract {

    @Autowired
    ApproveFirstConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return awaitReply(service.confirm(command));
    }

    @Override
    Class<?>[] otherServerComponents() {
        return new Class<?>[]{ApproveFirstHoldings.class, ApproveFirstConfirmService.class, ApproveFirstReplyListener.class};
    }

    @Override
    PaymentResult confirmOnOther(ConfigurableApplicationContext other, ConfirmPaymentCommand command) {
        return awaitReply(other.getBean(ApproveFirstConfirmService.class).confirm(command));
    }

    private static PaymentResult awaitReply(PaymentResult result) {
        if (result.status() == PaymentStatus.APPROVED) {
            await().atMost(Duration.ofSeconds(15)).until(() -> StockReplies.isReplied(result.orderId()));
        }
        return result;
    }
}
