package com.example.payment.infrastructure.pg;

import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * 12장 결정 4 비교: 시간 제한만(11장까지). 승인 요청은 2장 읽기 시간 제한만 있고 차단기가 없다 — 카드사가 답하지 않는 동안 모든 승인이 시간 제한까지
 * 기다리고 결과 모름으로 남는다
 */
@Primary
public class TimeoutOnlyPaymentGateway extends PgPaymentGateway {

    public TimeoutOnlyPaymentGateway(RestClient pgRestClient) {
        super(pgRestClient, DisabledCircuit.create());
    }
}
