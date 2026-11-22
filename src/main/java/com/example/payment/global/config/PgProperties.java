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
    // 12장: 거래 조회(대사)의 읽기 시간 제한. 토스페이먼츠는 이 요청이 60초까지 걸릴 수 있어 60초 이상으로 두라고 한다
    private final Duration transactionsReadTimeout;
}
