package com.example.payment.application.payment.comparison;

import com.example.inventory.domain.error.exception.OutOfStockException;
import com.example.inventory.domain.stock.StockLine;
import com.example.payment.application.payment.comparison.dto.StockReplyMessage;
import com.example.payment.application.payment.comparison.dto.StockRequestMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비교 구현(10장 결정 1, 승인 먼저)의 재고 서비스 쪽: 결제 서버가 보낸 재고 차감 요청을 읽어 줄이고, 줄였는지를 답 토픽으로 돌려준다.
 * 재고 서비스에 끼우는 시험 구성품이다(시험 설정 test.inventory.components). 수량은 예약 기록 없이 바로 줄인다(QuantityStockRecorder).
 */
public class ApproveFirstStockListener {

    private final QuantityStockRecorder quantityStockRecorder;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String repliesTopic;

    public ApproveFirstStockListener(QuantityStockRecorder quantityStockRecorder, KafkaTemplate<String, String> kafkaTemplate,
                                     JsonMapper jsonMapper, Environment environment) {
        this.quantityStockRecorder = quantityStockRecorder;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.repliesTopic = environment.getRequiredProperty("inventory.stock-commands.topic") + "-replies";
    }

    @KafkaListener(topics = "${inventory.stock-commands.topic}-requests")
    public void receive(ConsumerRecord<String, String> record) {
        StockRequestMessage request = jsonMapper.readValue(record.value(), StockRequestMessage.class);
        StockReplyMessage reply = new StockReplyMessage(request.orderId(), decrease(request));
        kafkaTemplate.send(repliesTopic, request.orderId(), jsonMapper.writeValueAsString(reply)).join();
    }

    private boolean decrease(StockRequestMessage request) {
        try {
            quantityStockRecorder.decrease(request.items().stream()
                    .map(item -> StockLine.of(item.productId(), item.quantity()))
                    .toList());
            return true;
        } catch (OutOfStockException outOfStock) {
            return false;
        }
    }
}
