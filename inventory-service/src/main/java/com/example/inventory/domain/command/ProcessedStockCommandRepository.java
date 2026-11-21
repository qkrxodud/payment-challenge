package com.example.inventory.domain.command;

/** 11장 결정 1: 처리한 재고 명령 기록 */
public interface ProcessedStockCommandRepository {

    // 이 번호의 명령을 아직 처리하지 않았으면 남기고 맞다고 답한다. 이미 있으면(다른 트랜잭션이 남기는 중이면 끝날 때까지 기다린 뒤) 아니라고 답한다
    boolean claim(ProcessedStockCommand command);
}
