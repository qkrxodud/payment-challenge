package com.example.inventory.domain.command;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 11장 결정 1: 처리한 재고 명령 한 건. 명령 번호는 결제 서버가 남긴 기록(payment_outbox)의 번호다 — 같은 명령이 다시 가면 같은 번호로 간다(10장 결정 5).
 * 예약을 바꾼 그 트랜잭션에서 남겨, 같은 번호가 다시 오면 아무것도 바꾸지 않는다.
 */
@Getter
public class ProcessedStockCommand {

    private final String commandId;
    private final Long reservationId;
    private final String command;
    private final Instant processedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private ProcessedStockCommand(String commandId, Long reservationId, String command, Instant processedAt) {
        this.commandId = commandId;
        this.reservationId = reservationId;
        this.command = command;
        this.processedAt = processedAt;
    }

    public static ProcessedStockCommand of(String commandId, Long reservationId, String command, Instant processedAt) {
        return ProcessedStockCommand.builder()
                .commandId(commandId)
                .reservationId(reservationId)
                .command(command)
                .processedAt(processedAt)
                .build();
    }
}
