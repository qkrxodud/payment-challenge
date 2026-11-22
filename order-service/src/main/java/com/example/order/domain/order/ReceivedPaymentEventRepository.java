package com.example.order.domain.order;

import java.time.Instant;

/** 9장: 받은 결제 이벤트 기록. 11장 결정 1부터 같은 고유 번호는 한 번만 남는다 */
public interface ReceivedPaymentEventRepository {

    // 11장 결정 1: 이 고유 번호의 이벤트를 아직 받지 않았으면 남기고 맞다고 답한다. 이미 받았으면(다른 트랜잭션이 남기는 중이면 끝날 때까지 기다린 뒤)
    // 남기지 않고 아니라고 답한다
    boolean claim(ReceivedPaymentEvent event);

    // 12장 결정 5: 이때보다 먼저 받은 기록을 limit 줄까지 지우고, 지운 수를 돌려준다
    int deleteReceivedBefore(Instant cutoff, int limit);
}
