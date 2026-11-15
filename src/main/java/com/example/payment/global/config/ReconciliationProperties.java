package com.example.payment.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결과 모름 보정 설정(4장).
 * waitAfterRequest: 승인을 요청한 뒤 이만큼 지난 결제만 보정한다. 카드사가 승인 요청을 처리하는 데 걸릴 수 있는 시간보다 길어야 한다.
 * interval: 보정 작업을 다시 돌리기 전에 쉬는 시간.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "payment.reconciliation")
public class ReconciliationProperties {

    private final Duration waitAfterRequest;
    private final Duration interval;
}
