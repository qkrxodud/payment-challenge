package com.example.inventory.application.command;

import com.example.inventory.application.reservation.ReservationRecorder;
import com.example.inventory.domain.command.ProcessedStockCommand;
import com.example.inventory.domain.command.ProcessedStockCommandRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 11장 결정 1: 결제 서버가 보낸 재고 명령(예약 확정·해제)을 처리한다. 명령 번호를 처리한 명령 기록에 먼저 남기고, 처음 온 번호일 때만 예약을 바꾼다 —
 * 같은 트랜잭션이라 예약이 바뀌면 번호도 반드시 남고, 바뀌지 못하면 번호도 남지 않아 다시 시도할 수 있다.
 * 10장 예약 상태 전이(같은 확정·해제는 이미 지난 상태라 바꾸지 않음)는 그대로 둔다. 이 기록은 그 위에 메시지마다 한 번만 처리하는 일반 규칙이다.
 * 11장 결정 2: 명령 번호는 결제 서버 payment_outbox 번호라 같은 예약의 명령 사이에서 만든 순서대로 커진다 — 예약 기록이 옛 명령을 거른다.
 */
@Service
@RequiredArgsConstructor
public class StockCommandService {

    private static final String CONFIRM = "CONFIRM";
    private static final String RELEASE = "RELEASE";

    private final ProcessedStockCommandRepository processedStockCommandRepository;
    private final ReservationRecorder reservationRecorder;
    private final Clock clock;

    @Transactional
    public void confirm(String commandId, Long reservationId) {
        if (processedStockCommandRepository.claim(ProcessedStockCommand.of(commandId, reservationId, CONFIRM, clock.instant()))) {
            reservationRecorder.confirm(reservationId, Long.parseLong(commandId));
        }
    }

    @Transactional
    public void release(String commandId, Long reservationId) {
        if (processedStockCommandRepository.claim(ProcessedStockCommand.of(commandId, reservationId, RELEASE, clock.instant()))) {
            reservationRecorder.release(reservationId, Long.parseLong(commandId));
        }
    }
}
