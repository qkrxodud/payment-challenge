package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentConfirmService;
import com.example.payment.application.payment.comparison.dto.StockRequestMessage;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.PaymentStatus;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비교 구현(10장 결정 1, 승인 먼저): 카드사 승인을 먼저 받고, 승인됐으면 재고 서비스에 Kafka 로 재고 차감을 보낸 뒤 바로 답한다 —
 * 결제 응답은 재고 서비스를 기다리지 않는다. 재고 서비스가 줄이지 못했다고 답하면 ApproveFirstReplyListener 가 카드사 취소로 보상한다
 * (고객 카드에 승인과 취소가 함께 남고 카드사를 한 번 더 부른다).
 */
public class ApproveFirstConfirmService {

    private final PaymentConfirmService paymentConfirmService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final String commandsTopic;

    public ApproveFirstConfirmService(PaymentConfirmService paymentConfirmService, KafkaTemplate<String, String> kafkaTemplate,
                                      JdbcTemplate jdbcTemplate, JsonMapper jsonMapper,
                                      @Value("${payment.inventory.commands-topic}") String commandsTopic) {
        this.paymentConfirmService = paymentConfirmService;
        this.kafkaTemplate = kafkaTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.commandsTopic = commandsTopic;
    }

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        PaymentResult result = paymentConfirmService.confirm(command);
        if (result.status() == PaymentStatus.APPROVED) {
            requestDecrease(command.orderId());
        }
        return result;
    }

    private void requestDecrease(String orderId) {
        List<StockRequestMessage.Line> items = jdbcTemplate.query("select i.product_id, i.quantity from payment_item i "
                        + "join payment p on p.id = i.payment_id where p.order_id = ?",
                (row, number) -> new StockRequestMessage.Line(row.getLong("product_id"), row.getInt("quantity")), orderId);
        kafkaTemplate.send(commandsTopic, orderId, jsonMapper.writeValueAsString(new StockRequestMessage(orderId, items))).join();
    }
}
