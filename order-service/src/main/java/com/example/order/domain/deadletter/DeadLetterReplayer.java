package com.example.order.domain.deadletter;

/**
 * 11장 결정 4: 실패 모음의 이벤트를 원래 토픽으로 다시 넣는다(운영자 명령). 다시 넣은 이벤트는 처음 온 이벤트와 같은 규칙으로 처리된다 —
 * 아직 반영하지 않은 이벤트면 반영되고, 이미 받은 번호거나(결정 1) 더 새 결과가 반영된 뒤면(결정 2) 주문을 바꾸지 않는다.
 */
public interface DeadLetterReplayer {

    // 지금까지 실패 모음에 쌓인 이벤트 가운데 아직 다시 넣지 않은 것을 모두 다시 넣고, 다시 넣은 수를 돌려준다
    int replayAll();
}
