package com.example.payment.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** PG 연결 설정. 시간 제한을 비워 두면 JDK HttpClient 는 답을 끝까지 기다린다 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "payment.pg")
public class PgProperties {

    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;
}
