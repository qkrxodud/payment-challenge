package com.example.payment.application.payment;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("결제 상태 이력 — 운영 구현")
class PaymentStatusHistoryTest extends StatusHistoryContract {

    @Override
    List<HistoryLine> historyOf(String orderId) {
        return historyIn(jdbcTemplate, orderId);
    }

    // 상태 이력 표에 남은 이 결제의 이력(남긴 순서대로). 바뀐 때는 JPA 가 UTC 시각으로 남긴다
    static List<HistoryLine> historyIn(JdbcTemplate jdbcTemplate, String orderId) {
        return jdbcTemplate.query("select h.from_status, h.to_status, h.action, h.changed_by, h.changed_at "
                        + "from payment_status_history h join payment p on p.id = h.payment_id where p.order_id = ? order by h.id",
                (row, number) -> HistoryLine.of(row.getString("from_status"), row.getString("to_status"), row.getString("action"),
                        row.getString("changed_by"), row.getObject("changed_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), orderId);
    }
}
