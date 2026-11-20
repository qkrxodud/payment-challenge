package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.comparison.dto.QuantityCommandMessage;
import com.example.payment.application.payment.comparison.dto.QuantityStockRequest;
import com.example.payment.domain.error.exception.PaymentEventNotSentException;
import com.example.payment.domain.outbox.OutboxType;
import com.example.payment.domain.stock.StockCommand;
import com.example.payment.domain.stock.StockCommandSender;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비교 구현(10장 결정 3, 수량만): 운영과 같이 확정·해제를 payment_outbox 에 남겨 릴레이로 보내되(결정 5), 예약 기록이 없는 재고 서비스가
 * 늘릴 수 있게 담긴 상품과 수량을 함께 싣는다. 재고 서비스는 QuantityStockCommandListener 가 읽는다(같은 명령이 다시 오면 다시 늘린다).
 */
@Primary
public class QuantityStockCommandSender implements StockCommandSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final String topic;

    public QuantityStockCommandSender(KafkaTemplate<String, String> kafkaTemplate, JdbcTemplate jdbcTemplate,
                                      JsonMapper jsonMapper, Environment environment) {
        this.kafkaTemplate = kafkaTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.topic = environment.getRequiredProperty("payment.inventory.commands-topic") + "-quantity";
    }

    @Override
    public void send(StockCommand command) {
        List<QuantityStockRequest.Line> items = jdbcTemplate.query(
                "select product_id, quantity from payment_item where payment_id = ?",
                (row, number) -> new QuantityStockRequest.Line(row.getLong("product_id"), row.getInt("quantity")),
                command.getReservationId());
        String name = command.getType() == OutboxType.STOCK_CONFIRM ? "CONFIRM" : "RELEASE";
        try {
            kafkaTemplate.send(topic, String.valueOf(command.getReservationId()),
                    jsonMapper.writeValueAsString(new QuantityCommandMessage(name, items))).join();
        } catch (CompletionException notConfirmed) {
            throw new PaymentEventNotSentException(command.getCommandId(), notConfirmed.getCause().getClass().getSimpleName());
        }
    }
}
