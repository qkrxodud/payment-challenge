package com.example.payment.application.payment.comparison;

import com.example.payment.domain.payment.PaymentEvent;
import com.example.payment.domain.payment.PaymentEventSender;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.PaymentEventNotSentException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 비교 구현(9장 결정 3): 행 단위로 나눠 보내기. 서버마다 작업 잠금 없이 돌고, 아직 보내지 않은 결과를 한 줄씩
 * select … for update skip locked 로 잠가 맡는다 — 다른 서버가 맡아 잠근 줄은 건너뛰고 그다음 줄을 맡는다. 맡은 줄을 보내고,
 * 받았다는 답이 오면 보낸 때를 남기고 커밋해 잠금을 푼다. 보낼 줄이 없거나 보내지 못하면 이번 주기를 멈춘다(보내지 못한 줄은 되돌려 다음에 다시).
 * 서버를 늘리면 함께 보낼 수 있다. 보내기·키는 운영 구현과 같다.
 */
@Slf4j
public class SkipLockedOutboxRelay {

    private final JdbcTemplate jdbcTemplate;
    private final PaymentEventSender paymentEventSender;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public SkipLockedOutboxRelay(JdbcTemplate jdbcTemplate, PaymentEventSender paymentEventSender,
                                 PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.paymentEventSender = paymentEventSender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void relay() {
        boolean sent = relayNext();
        while (sent) {
            sent = relayNext();
        }
    }

    private boolean relayNext() {
        return Boolean.TRUE.equals(transactionTemplate.execute(this::relayNext));
    }

    private Boolean relayNext(TransactionStatus transaction) {
        List<PaymentEvent> claimed = jdbcTemplate.query("select id, payment_id, order_id, status, occurred_at from payment_outbox "
                        + "where sent_at is null order by id limit 1 for update skip locked",
                (row, number) -> PaymentEvent.of(String.valueOf(row.getLong("id")), row.getLong("payment_id"),
                        row.getString("order_id"), PaymentStatus.valueOf(row.getString("status")),
                        // 운영 결과는 JPA 가 UTC 로 남긴다. JDBC 시각(getTimestamp)으로 읽으면 JVM 시간대로 읽혀 9시간 어긋난다(측정에서 봄)
                        row.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)));
        if (claimed.isEmpty()) {
            return false;
        }
        PaymentEvent event = claimed.get(0);
        try {
            paymentEventSender.send(event);
        } catch (PaymentEventNotSentException notSent) {
            log.warn("Could not send payment event {}. It will be sent again next time.", event.getEventId());
            transaction.setRollbackOnly();
            return false;
        }
        jdbcTemplate.update("update payment_outbox set sent_at = ? where id = ?", Timestamp.from(clock.instant()),
                Long.valueOf(event.getEventId()));
        return true;
    }
}
