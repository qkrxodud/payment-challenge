package com.example.inventory.presentation.command;

import com.example.inventory.domain.deadletter.DeadLetterReplayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 11장 결정 4: 운영자가 실패 모음을 원래 토픽으로 다시 넣는 명령. 재고 서비스를 다시 넣기 모드로 한 번 띄우면 다시 넣고 끝난다 —
 * java -jar inventory-service.jar --spring.config.name=inventory-service --inventory.dead-letter.replay=true --spring.main.web-application-type=none
 * --spring.kafka.listener.auto-startup=false
 * (리스너를 띄우지 않아 이 프로세스는 재고 명령을 읽지 않는다. 다시 넣은 명령은 떠 있는 재고 서비스가 읽는다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.dead-letter.replay", havingValue = "true")
public class DeadLetterReplayCommand implements ApplicationRunner {

    private final DeadLetterReplayer deadLetterReplayer;

    @Override
    public void run(ApplicationArguments arguments) {
        int replayed = deadLetterReplayer.replayAll();
        log.info("Replayed {} dead letter stock commands to the original topic.", replayed);
    }
}
