package com.example.order.domain.deadletter;

import java.util.OptionalLong;

/** 12장 결정 5: 실패 모음에 쌓여 아직 다시 넣지 않은 이벤트 수(운영 지표) */
public interface DeadLetterBacklog {

    // 실패 모음에 남은 이벤트 가운데 다시 넣기 명령이 아직 지나가지 않은 수. 브로커에 묻지 못하면 비어 있다
    OptionalLong pending();
}
