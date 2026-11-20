package com.example.payment.infrastructure.kafka;

import com.example.payment.domain.error.exception.PaymentEventNotSentException;
import com.example.payment.domain.stock.StockCommand;
import com.example.payment.domain.stock.StockCommandSender;
import com.example.payment.global.config.InventoryProperties;
import com.example.payment.infrastructure.kafka.dto.StockCommandMessage;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 10장 결정 5: 재고 명령을 JSON 으로 재고 명령 토픽에 보내고, 브로커가 받았다고 답할 때까지 기다린다(9장 결제 이벤트와 같은 프로듀서).
 * 메시지 키는 결제 번호(= 예약 번호)다 — 같은 예약의 확정·해제는 같은 파티션에 실려 재고 서비스는 보낸 순서대로 읽는다.
 */
@Component
@RequiredArgsConstructor
public class KafkaStockCommandSender implements StockCommandSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final InventoryProperties inventoryProperties;
    private final JsonMapper jsonMapper;

    @Override
    public void send(StockCommand command) {
        String body = jsonMapper.writeValueAsString(StockCommandMessage.from(command));
        try {
            kafkaTemplate.send(inventoryProperties.getCommandsTopic(), String.valueOf(command.getReservationId()), body).join();
        } catch (CompletionException notConfirmed) {
            throw new PaymentEventNotSentException(command.getCommandId(), notConfirmed.getCause().getClass().getSimpleName());
        } catch (KafkaException notSent) {
            throw new PaymentEventNotSentException(command.getCommandId(), notSent.getClass().getSimpleName());
        }
    }
}
