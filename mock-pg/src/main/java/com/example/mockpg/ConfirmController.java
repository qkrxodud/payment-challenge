package com.example.mockpg;

import com.example.mockpg.dto.ConfirmRequest;
import com.example.mockpg.dto.ConfirmResponse;
import com.example.mockpg.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 서버가 부르는 승인 API. 시나리오만큼 기다렸다가 승인하거나 거절한다. 승인할 때는 받은 금액을 기록한다.
 * 4장부터 거절도 기록하고, 결과를 기록할 때까지를 처리 중으로 본다.
 */
@RestController
@RequiredArgsConstructor
public class ConfirmController {

    private final ScenarioStore scenarioStore;

    @PostMapping("/v1/payments/confirm")
    public ResponseEntity<Object> confirm(@RequestBody ConfirmRequest request) throws InterruptedException {
        Scenario scenario = scenarioStore.current();
        scenarioStore.enter(request.orderId());
        try {
            Thread.sleep(scenario.getDelay().toMillis());
            return answer(scenario.getOutcome(), request);
        } finally {
            scenarioStore.leave(request.orderId());
        }
    }

    private ResponseEntity<Object> answer(Outcome outcome, ConfirmRequest request) {
        if (outcome.isFail() || outcome.isLost()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("FAILED_INTERNAL_SYSTEM_PROCESSING", "The gateway failed to process this request."));
        }
        if (outcome.isDecline()) {
            scenarioStore.recordDecline(request.paymentKey(), request.orderId(), request.amount());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("CARD_REJECTED", "The card company rejected this payment."));
        }
        // 결제 서버가 이미 기다리기를 그만뒀어도 PG 쪽 승인은 남는다.
        // 받은 금액을 그대로 승인한다 — 고객이 결제창에서 인증한 금액이 곧 이 요청의 금액이라고 본다
        scenarioStore.recordApproval(request.paymentKey(), request.orderId(), request.amount());
        return ResponseEntity.ok(ConfirmResponse.done(request));
    }
}
