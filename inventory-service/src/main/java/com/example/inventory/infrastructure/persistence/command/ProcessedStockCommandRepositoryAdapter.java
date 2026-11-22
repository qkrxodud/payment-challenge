package com.example.inventory.infrastructure.persistence.command;

import com.example.inventory.domain.command.ProcessedStockCommand;
import com.example.inventory.domain.command.ProcessedStockCommandRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 11장 결정 1: 처리한 명령은 명령 번호가 두 번 오를 수 없는 표에 insert ignore 한 문장으로 남긴다(예약 남기기 claim 과 같은 방식).
 * 예약을 바꾸는 트랜잭션 안에서 부르므로 예약이 바뀌지 못하고 되돌려지면 이 기록도 함께 되돌려진다. 시각은 UTC 로 남긴다.
 */
@Repository
@RequiredArgsConstructor
public class ProcessedStockCommandRepositoryAdapter implements ProcessedStockCommandRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean claim(ProcessedStockCommand command) {
        return jdbcTemplate.update("insert ignore into processed_stock_command (command_id, reservation_id, command, processed_at) "
                        + "values (?, ?, ?, ?)", command.getCommandId(), command.getReservationId(), command.getCommand(),
                LocalDateTime.ofInstant(command.getProcessedAt(), ZoneOffset.UTC)) == 1;
    }

    // 12장 결정 5: 처리한 때는 남길 때와 같이 UTC 시각으로 견준다
    @Override
    public int deleteProcessedBefore(Instant cutoff, int limit) {
        return jdbcTemplate.update("delete from processed_stock_command where processed_at < ? order by id limit ?",
                LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC), limit);
    }
}
