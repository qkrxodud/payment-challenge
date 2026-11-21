package com.example.inventory.global.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 11장 결정 4: 실패 모음. topic: 다시 시도를 다 쓴 재고 명령과 영구 오류 명령를 모으는 토픽(<재고 명령 토픽>.DLT).
 * partitions: 실패 모음 토픽의 파티션 수 — 원래 명령이 실린 파티션과 같은 번호에 싣기 때문에 재고 명령 토픽과 같아야 한다.
 * sourceTopic: 다시 넣을 원래 토픽(재고 명령 토픽).
 * replayGroup: 운영자가 실패 모음을 원래 토픽으로 다시 넣는 명령의 소비자 그룹 — 어디까지 다시 넣었는지 이 그룹이 기억한다.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "inventory.dead-letter")
public class DeadLetterProperties {

    private final String topic;
    private final int partitions;
    private final String sourceTopic;
    private final String replayGroup;
}
