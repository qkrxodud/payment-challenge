package com.example.inventory.infrastructure.persistence.reservation;

import com.example.inventory.domain.reservation.ReservationStatus;
import com.example.inventory.domain.reservation.StockReservation;
import com.example.inventory.domain.reservation.StockReservationRepository;
import com.example.inventory.domain.stock.StockLine;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 10장 결정 3: 예약 기록. 예약 번호(결제 번호)가 기본 키라 같은 번호의 예약은 한 줄뿐이다.
 * 남기기(claim)는 insert ignore 한 문장이다 — 같은 번호를 다른 트랜잭션이 남기는 중이면 그 트랜잭션이 끝날 때까지 기다린 뒤,
 * 이미 있으면 아무것도 남기지 않는다. 그래서 예약과 해제가 겹쳐도 한 줄만 남고, 나중 쪽은 먼저 남은 줄을 잠그고 읽어 판단한다.
 * 시각은 UTC 로 남긴다.
 */
@Repository
@RequiredArgsConstructor
public class StockReservationRepositoryAdapter implements StockReservationRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean claim(StockReservation reservation) {
        LocalDateTime now = utc(reservation.getUpdatedAt());
        int inserted = jdbcTemplate.update("insert ignore into stock_reservation "
                        + "(reservation_id, status, last_command_id, created_at, updated_at) values (?, ?, ?, ?, ?)",
                reservation.getReservationId(), reservation.getStatus().name(), reservation.getLastCommandId(), now, now);
        if (inserted == 0) {
            return false;
        }
        reservation.getItems().forEach(item -> jdbcTemplate.update(
                "insert into stock_reservation_item (reservation_id, product_id, quantity) values (?, ?, ?)",
                reservation.getReservationId(), item.getProductId(), item.getQuantity()));
        return true;
    }

    @Override
    public Optional<StockReservation> findForUpdate(Long reservationId) {
        return find("select reservation_id, status, last_command_id, updated_at from stock_reservation "
                + "where reservation_id = ? for update", reservationId);
    }

    @Override
    public Optional<StockReservation> findById(Long reservationId) {
        return find("select reservation_id, status, last_command_id, updated_at from stock_reservation where reservation_id = ?",
                reservationId);
    }

    // 예약 줄을 다 읽은 뒤에 담긴 상품을 읽는다. 줄을 읽는 도중(행 매퍼 안)에 상품을 읽으면 트랜잭션 밖의 조회는 연결을 하나 더 쥐어,
    // 조회가 몰리면 연결 풀이 바닥나 서로 기다린다(10장 결정 4 조회 비교의 몰림 시험에서 실행으로 봄)
    private Optional<StockReservation> find(String sql, Long reservationId) {
        List<StockReservation> found = jdbcTemplate.query(sql, (row, number) -> StockReservation.of(reservationId, List.of(),
                ReservationStatus.valueOf(row.getString("status")), row.getObject("last_command_id", Long.class),
                row.getObject("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), reservationId);
        return found.stream()
                .findFirst()
                .map(reservation -> StockReservation.of(reservationId, itemsOf(reservationId), reservation.getStatus(),
                        reservation.getLastCommandId(), reservation.getUpdatedAt()));
    }

    private List<StockLine> itemsOf(Long reservationId) {
        return jdbcTemplate.query("select product_id, quantity from stock_reservation_item where reservation_id = ? order by id",
                (row, number) -> StockLine.of(row.getLong("product_id"), row.getInt("quantity")), reservationId);
    }

    @Override
    public void updateStatus(StockReservation reservation) {
        jdbcTemplate.update("update stock_reservation set status = ?, last_command_id = ?, updated_at = ? where reservation_id = ?",
                reservation.getStatus().name(), reservation.getLastCommandId(), utc(reservation.getUpdatedAt()),
                reservation.getReservationId());
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
