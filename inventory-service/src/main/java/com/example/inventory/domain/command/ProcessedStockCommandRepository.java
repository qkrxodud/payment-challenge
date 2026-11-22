package com.example.inventory.domain.command;

import java.time.Instant;

/** 11장 결정 1: 처리한 재고 명령 기록 */
public interface ProcessedStockCommandRepository {

    // 이 번호의 명령을 아직 처리하지 않았으면 남기고 맞다고 답한다. 이미 있으면(다른 트랜잭션이 남기는 중이면 끝날 때까지 기다린 뒤) 아니라고 답한다
    boolean claim(ProcessedStockCommand command);

    // 12장 결정 5: 이때보다 먼저 처리한 기록을 limit 줄까지 지우고, 지운 수를 돌려준다
    int deleteProcessedBefore(Instant cutoff, int limit);
}
