package com.example.payment.application.payment.comparison;

import com.example.inventory.application.command.StockCommandService;
import com.example.inventory.application.reservation.ReservationRecorder;
import com.example.inventory.domain.command.ProcessedStockCommandRepository;
import java.time.Clock;
import org.springframework.context.annotation.Primary;

/**
 * 비교 구현(11장 결정 1, 명령 번호로 가리지 않음 — 10장 재고 서비스): 처리한 명령 기록을 남기지 않고 예약 상태 전이(10장 결정 3)만으로 같은 확정·해제를 흡수한다.
 * 결정 2(예약마다 마지막 명령 번호로 옛 명령 거르기)는 그대로 둔다. 재고 서비스에 끼우는 시험 구성품이다(test.inventory.components).
 */
@Primary
public class NoDedupStockCommandService extends StockCommandService {

    private final ReservationRecorder recorder;

    public NoDedupStockCommandService(ProcessedStockCommandRepository processedStockCommandRepository,
                                      ReservationRecorder reservationRecorder, Clock clock) {
        super(processedStockCommandRepository, reservationRecorder, clock);
        this.recorder = reservationRecorder;
    }

    @Override
    public void confirm(String commandId, Long reservationId) {
        recorder.confirm(reservationId, Long.parseLong(commandId));
    }

    @Override
    public void release(String commandId, Long reservationId) {
        recorder.release(reservationId, Long.parseLong(commandId));
    }
}
