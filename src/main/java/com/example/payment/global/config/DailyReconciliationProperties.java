package com.example.payment.global.config;

import java.time.ZoneId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 12장 결정 1: 하루치 대사 설정.
 * zone: 하루를 가르는 시간대(카드사 거래 조회의 날짜와 우리 결제의 인증한 날을 같은 시간대로 자른다).
 * pageSize: 카드사 거래 조회 한 번에 받는 거래 수(토스페이먼츠 최대 5000).
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "payment.daily-reconciliation")
public class DailyReconciliationProperties {

    private final ZoneId zone;
    private final int pageSize;
}
