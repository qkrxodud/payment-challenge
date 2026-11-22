package com.example.order.infrastructure.persistence.order;

import com.example.order.domain.order.ReceivedPaymentEvent;
import com.example.order.domain.order.ReceivedPaymentEventRepository;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 11장 결정 1: 받은 이벤트는 고유 번호(event_id)가 두 번 오를 수 없는 표에 insert ignore 한 문장으로 남긴다 — 같은 번호가 이미 있으면 아무것도
 * 남기지 않고 아니라고 답한다(같은 번호를 다른 트랜잭션이 남기는 중이면 그 트랜잭션이 끝날 때까지 기다린 뒤). 주문을 바꾸는 트랜잭션 안에서 부르므로
 * 주문이 바뀌지 못하고 되돌려지면 이 기록도 함께 되돌려진다. 시각은 JPA 로 남기던 9장과 같게 JDBC Timestamp 로 남긴다.
 */
@Repository
@RequiredArgsConstructor
public class ReceivedPaymentEventRepositoryAdapter implements ReceivedPaymentEventRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean claim(ReceivedPaymentEvent event) {
        return jdbcTemplate.update("insert ignore into received_payment_event "
                        + "(event_id, order_id, payment_status, occurred_at, partition_no, offset_no, received_at) values (?, ?, ?, ?, ?, ?, ?)",
                event.getEventId(), event.getOrderId(), event.getPaymentStatus(), Timestamp.from(event.getOccurredAt()),
                event.getPartitionNo(), event.getOffsetNo(), Timestamp.from(event.getReceivedAt())) == 1;
    }

    // 12장 결정 5: 받은 때는 남길 때와 같이 JDBC Timestamp 로 견준다
    @Override
    public int deleteReceivedBefore(Instant cutoff, int limit) {
        return jdbcTemplate.update("delete from received_payment_event where received_at < ? order by id limit ?", Timestamp.from(cutoff),
                limit);
    }
}
