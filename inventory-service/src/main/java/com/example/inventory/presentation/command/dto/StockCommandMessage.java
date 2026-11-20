package com.example.inventory.presentation.command.dto;

/** 10장 결정 5: 결제 서버가 보낸 재고 명령(JSON). command 는 CONFIRM 또는 RELEASE 다 */
public record StockCommandMessage(String commandId, Long reservationId, String orderId, String command) {

    public boolean isConfirm() {
        return "CONFIRM".equals(command);
    }
}
