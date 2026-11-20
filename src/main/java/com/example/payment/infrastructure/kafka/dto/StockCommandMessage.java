package com.example.payment.infrastructure.kafka.dto;

import com.example.payment.domain.outbox.OutboxType;
import com.example.payment.domain.stock.StockCommand;

/** 10장 결정 5: 브로커에 싣는 재고 명령의 본문(JSON). command 는 CONFIRM 또는 RELEASE 다 */
public record StockCommandMessage(String commandId, Long reservationId, String orderId, String command) {

    public static StockCommandMessage from(StockCommand stockCommand) {
        return new StockCommandMessage(stockCommand.getCommandId(), stockCommand.getReservationId(), stockCommand.getOrderId(),
                commandOf(stockCommand.getType()));
    }

    private static String commandOf(OutboxType type) {
        if (type == OutboxType.STOCK_CONFIRM) {
            return "CONFIRM";
        }
        return "RELEASE";
    }
}
