package com.example.payment.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 10장: 재고 서비스 연결 설정. 시간 제한을 비워 두면 JDK HttpClient 는 답을 끝까지 기다린다 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "payment.inventory")
public class InventoryProperties {

    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    // 10장 결정 5: 예약 확정·해제를 보내는 토픽. 재고 서비스가 읽는다
    private final String commandsTopic;
}
