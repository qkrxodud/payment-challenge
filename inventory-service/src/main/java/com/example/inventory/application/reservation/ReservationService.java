package com.example.inventory.application.reservation;

import com.example.inventory.domain.error.exception.ReservationNotFoundException;
import com.example.inventory.domain.reservation.StockReservation;
import com.example.inventory.domain.reservation.StockReservationRepository;
import com.example.inventory.domain.stock.StockLine;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 10장 결정 3: 재고 예약. 결제 서버가 결제 번호를 예약 번호로 재고를 잡고(예약), 결제 결과에 따라 확정하거나 해제한다.
 * 수량만 바로 깎고 늘리면 어느 결제가 무엇을 잡았는지 남지 않아, 해제가 예약보다 먼저 오거나 같은 해제가 두 번 오면 재고가 틀어진다.
 * 예약 줄 하나에 상태(잡음 → 확정 / 해제)를 두면 늦게 온 예약은 먼저 남은 해제 줄에 막히고, 다시 온 확정·해제는 이미 지난 상태라 바꾸지 않는다.
 * 11장: 확정·해제(재고 명령)는 StockCommandService 가 명령 번호를 남기며 ReservationRecorder 로 바꾼다. 이 서비스는 HTTP 예약·조회만 맡는다.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRecorder reservationRecorder;
    private final StockReservationRepository stockReservationRepository;

    public void reserve(Long reservationId, List<StockLine> items) {
        reservationRecorder.reserve(reservationId, items);
    }

    public StockReservation find(Long reservationId) {
        return stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}
