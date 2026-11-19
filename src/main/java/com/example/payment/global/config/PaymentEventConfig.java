package com.example.payment.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 9장: 결제 이벤트 설정과 릴레이 설정을 읽는다 */
@Configuration
@EnableConfigurationProperties({PaymentEventProperties.class, OutboxProperties.class})
public class PaymentEventConfig {
}
