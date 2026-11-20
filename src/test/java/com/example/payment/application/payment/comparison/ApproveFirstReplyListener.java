package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentCancelService;
import com.example.payment.application.payment.comparison.dto.StockReplyMessage;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비교 구현(10장 결정 1, 승인 먼저): 결제 서버가 재고 서비스의 답을 읽는다. 줄이지 못했으면 승인한 결제를 카드사에 전액 취소한다(보상).
 * 같은 결제 서버들은 한 소비자 그룹으로 답을 나눠 읽는다.
 */
@RequiredArgsConstructor
public class ApproveFirstReplyListener {

    private final PaymentCancelService paymentCancelService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${payment.inventory.commands-topic}-replies",
            groupId = "${payment.inventory.commands-topic}-payment",
            properties = "auto.offset.reset=earliest")
    public void receive(ConsumerRecord<String, String> record) {
        StockReplyMessage reply = jsonMapper.readValue(record.value(), StockReplyMessage.class);
        if (reply.decreased()) {
            StockReplies.decreased(reply.orderId());
            StockReplies.replied(reply.orderId());
            return;
        }
        paymentCancelService.cancel(new CancelPaymentCommand(reply.orderId(), "not-held-" + reply.orderId(), null,
                "Out of stock after approval"));
        StockReplies.replied(reply.orderId());
    }
}
