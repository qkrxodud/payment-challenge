package com.example.payment.application.payment.comparison;

import com.example.inventory.domain.stock.StockLine;
import com.example.payment.application.payment.comparison.dto.QuantityCommandMessage;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비교 구현(10장 결정 3, 수량만)의 재고 서비스 쪽: 해제 명령이 오면 실린 수량만큼 바로 늘린다. 확정은 할 것이 없다.
 * 어느 예약의 명령인지 기록하지 않으므로 같은 해제가 다시 오면 다시 늘린다.
 * 재고 서비스에 끼우는 시험 구성품이다(시험 설정 test.inventory.components).
 */
@RequiredArgsConstructor
public class QuantityStockCommandListener {

    private final QuantityStockRecorder quantityStockRecorder;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${inventory.stock-commands.topic}-quantity")
    public void receive(ConsumerRecord<String, String> record) {
        QuantityCommandMessage command = jsonMapper.readValue(record.value(), QuantityCommandMessage.class);
        if (command.isRelease()) {
            quantityStockRecorder.increase(command.items().stream()
                    .map(item -> StockLine.of(item.productId(), item.quantity()))
                    .toList());
        }
    }
}
