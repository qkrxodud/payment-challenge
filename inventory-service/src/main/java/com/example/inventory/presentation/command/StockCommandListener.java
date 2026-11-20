package com.example.inventory.presentation.command;

import com.example.inventory.application.reservation.ReservationService;
import com.example.inventory.presentation.command.dto.StockCommandMessage;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 10장 결정 5: 결제 서버가 보낸 예약 확정·해제를 읽는다. 파티션마다 따로 읽는다(concurrency 2).
 * 같은 명령이 다시 와도(결제 서버 릴레이는 적어도 한 번 보낸다) 예약 상태 전이(결정 3)로 한 번만 반영한다 — 받은 명령 번호로 거르는 일반 중복 가리기,
 * 실패한 명령의 재시도·따로 모으기는 11장이다(처리에 실패하면 Spring Kafka 기본 처리를 그대로 둔다).
 */
@Component
@RequiredArgsConstructor
public class StockCommandListener {

    private final ReservationService reservationService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${inventory.stock-commands.topic}")
    public void receive(ConsumerRecord<String, String> record) {
        StockCommandMessage command = jsonMapper.readValue(record.value(), StockCommandMessage.class);
        if (command.isConfirm()) {
            reservationService.confirm(command.reservationId());
            return;
        }
        reservationService.release(command.reservationId());
    }
}
