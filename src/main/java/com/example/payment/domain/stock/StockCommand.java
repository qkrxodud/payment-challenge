package com.example.payment.domain.stock;

import com.example.payment.domain.outbox.OutboxType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 10장 결정 5: 재고 서비스에 보내는 재고 명령 한 건(예약 확정 또는 해제). 예약 번호는 결제 번호다.
 * commandId 는 남긴 기록의 번호다 — 같은 명령이 다시 가면 같은 번호로 간다(적어도 한 번).
 */
@Getter
public class StockCommand {

    private final String commandId;
    private final Long reservationId;
    private final String orderId;
    private final OutboxType type;

    @Builder(access = AccessLevel.PRIVATE)
    private StockCommand(String commandId, Long reservationId, String orderId, OutboxType type) {
        this.commandId = commandId;
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.type = type;
    }

    public static StockCommand of(String commandId, Long reservationId, String orderId, OutboxType type) {
        return StockCommand.builder()
                .commandId(commandId)
                .reservationId(reservationId)
                .orderId(orderId)
                .type(type)
                .build();
    }
}
