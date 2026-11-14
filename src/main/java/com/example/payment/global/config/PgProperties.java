package com.example.payment.global.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** PG 연결 설정 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "payment.pg")
public class PgProperties {

    private final String baseUrl;
}
