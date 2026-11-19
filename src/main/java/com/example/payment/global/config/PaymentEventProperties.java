package com.example.payment.global.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 9장: 결제 이벤트 설정.
 * topic: 결제 이벤트를 보내는 토픽. partitions: 서버가 뜰 때 토픽이 없으면 이 수만큼의 파티션으로 만든다 — 받는 쪽은 파티션마다 따로 읽는다.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "payment.events")
public class PaymentEventProperties {

    private final String topic;
    private final int partitions;
}
