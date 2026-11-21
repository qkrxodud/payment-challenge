package com.example.inventory.presentation.command;

import com.example.inventory.application.command.StockCommandService;
import com.example.inventory.domain.error.exception.UnknownStockCommandException;
import com.example.inventory.presentation.command.dto.StockCommandMessage;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 10장 결정 5: 결제 서버가 보낸 예약 확정·해제를 읽는다. 파티션마다 따로 읽는다(concurrency 2).
 * 같은 명령이 다시 와도(결제 서버 릴레이는 적어도 한 번 보낸다) 예약 상태 전이(결정 3)로 한 번만 반영한다.
 * 11장 결정 1: 명령 번호(commandId — 결제 서버 payment_outbox 번호)를 처리한 명령 기록에 남겨 같은 번호는 한 번만 처리한다(StockCommandService).
 * 11장 결정 3: 확정도 해제도 아닌 명령은 예약을 바꾸지 않고 영구 오류로 알린다(10장은 확정이 아니면 해제했다). 실패하면 같은 자리에서 간격을 늘려 다시 시도하고,
 * 영구 오류는 다시 시도하지 않는다(KafkaConsumerConfig).
 */
@Component
@RequiredArgsConstructor
public class StockCommandListener {

    private final StockCommandService stockCommandService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${inventory.stock-commands.topic}")
    public void receive(ConsumerRecord<String, String> record) {
        StockCommandMessage command = jsonMapper.readValue(record.value(), StockCommandMessage.class);
        command.requireComplete();
        if (command.isConfirm()) {
            stockCommandService.confirm(command.commandId(), command.reservationId());
            return;
        }
        if (command.isRelease()) {
            stockCommandService.release(command.commandId(), command.reservationId());
            return;
        }
        throw new UnknownStockCommandException(command.commandId());
    }
}
