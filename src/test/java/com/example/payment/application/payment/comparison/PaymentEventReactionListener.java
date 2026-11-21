package com.example.payment.application.payment.comparison;

import com.example.inventory.application.command.StockCommandService;
import com.example.payment.infrastructure.kafka.dto.PaymentEventMessage;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비교 구현(10장 결정 2, 코레오그래피)의 재고 서비스 쪽: 결제 이벤트(9장 결제 결과)를 읽고 재고 서비스가 스스로 정한다 —
 * 결제가 승인(승인·매입)이면 예약을 확정하고, 승인 없이 끝났거나 취소됐으면(승인 실패·만료·승인 취소·매입 취소) 해제한다.
 * 그러려면 재고 서비스가 결제 상태 이름 여섯 개와 그 뜻(어느 것이 재고를 돌려줄 때인가)을 알아야 한다.
 * 11장: 결제 이벤트 고유 번호(결제 서버 payment_outbox 번호 — 재고 명령 번호와 같은 줄 번호)를 명령 번호로 넘긴다.
 * 재고 서비스에 끼우는 시험 구성품이다(시험 설정 test.inventory.components, test.inventory.reads-payment-events=true).
 */
@RequiredArgsConstructor
public class PaymentEventReactionListener {

    private static final Set<String> CONFIRMING = Set.of("APPROVED", "CAPTURED");
    private static final Set<String> RELEASING = Set.of("APPROVAL_FAILED", "EXPIRED", "APPROVAL_CANCELED", "CAPTURE_CANCELED");

    private final StockCommandService stockCommandService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${inventory.payment-events.topic}")
    public void receive(ConsumerRecord<String, String> record) {
        PaymentEventMessage event = jsonMapper.readValue(record.value(), PaymentEventMessage.class);
        if (CONFIRMING.contains(event.status())) {
            stockCommandService.confirm(event.eventId(), event.paymentId());
            return;
        }
        if (RELEASING.contains(event.status())) {
            stockCommandService.release(event.eventId(), event.paymentId());
        }
    }
}
