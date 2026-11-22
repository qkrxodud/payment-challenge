package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NoStatusHistory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/**
 * 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다.
 * 상태 이력 표 없이, 9장 결제 결과 기록(payment_outbox 의 결제 결과 줄)을 이력으로 읽는다. 그 기록은 주문 서비스에 알릴 결과 상태와 남긴 때만 싣는다 —
 * 이전 상태·행위·계기 칸은 비어 있고, 결과가 아닌 상태(인증, 결과 모름)는 없다. 보낸 뒤에는 결정 5가 보존 기간 뒤 지운다
 */
@Tag("known-failing")
@Import(NoStatusHistory.class)
@DisplayName("결제 상태 이력 — 비교(결정 3): 결제 결과 기록(outbox)으로 대신함")
class OutboxAsHistoryTest extends StatusHistoryContract {

    @Override
    List<HistoryLine> historyOf(String orderId) {
        return jdbcTemplate.query("select status, occurred_at from payment_outbox where order_id = ? and type = 'PAYMENT_RESULT' order by id",
                (row, number) -> HistoryLine.of(null, row.getString("status"), null, null, row.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                orderId);
    }
}
