package com.example.inventory.presentation.command.dto;

import com.example.inventory.domain.error.exception.InvalidStockCommandException;
import java.util.Objects;

/** 10장 결정 5: 결제 서버가 보낸 재고 명령(JSON). command 는 CONFIRM 또는 RELEASE 다 */
public record StockCommandMessage(String commandId, Long reservationId, String orderId, String command) {

    public boolean isConfirm() {
        return "CONFIRM".equals(command);
    }

    // 11장 결정 3: 10장은 확정이 아니면 모두 해제로 보았다(모르는 명령도 해제)
    public boolean isRelease() {
        return "RELEASE".equals(command);
    }

    // 11장 결정 3: 명령 번호(결제 서버 payment_outbox 번호)와 예약 번호가 없거나 명령 번호가 숫자가 아니면 다시 시도해도 같다(영구 오류)
    public void requireComplete() {
        if (Objects.isNull(reservationId) || isNotCommandNumber(commandId)) {
            throw new InvalidStockCommandException();
        }
    }

    private static boolean isNotCommandNumber(String value) {
        return Objects.isNull(value) || value.isEmpty() || value.length() > 18
                || value.chars().anyMatch(digit -> digit < '0' || digit > '9');
    }
}
