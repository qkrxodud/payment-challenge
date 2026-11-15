package com.example.mockpg;

import com.example.mockpg.dto.CancelRequest;
import com.example.mockpg.dto.ErrorResponse;
import com.example.mockpg.dto.PaymentResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4장부터: 승인 뒤에 오는 결제 조회와 취소. 경로와 오류 코드는 토스페이먼츠 API 를 따른다.
 * 토스페이먼츠는 주문번호 조회를 "승인된 결제"의 조회로 설명한다. 여기서는 거절(ABORTED)도 조회로 돌려준다고 가정한다.
 * 둘 다 시나리오의 followUpDelay 만큼 늦게 답한다. 취소는 기다린 뒤에 처리하므로, 결제 서버가 기다리기를 그만둬도 취소는 남는다.
 * 5장부터 부분 취소(cancelAmount)와 멱등키(Idempotency-Key 헤더)를 받는다. 토스페이먼츠 문서에서 확인한 동작만 흉내 낸다:
 * - 같은 멱등키로 다시 오면 다시 처리하지 않고 첫 응답을 그대로 돌려준다
 * - 같은 멱등키의 앞 요청이 아직 처리 중이면 409 IDEMPOTENT_REQUEST_PROCESSING
 * - 같은 요청인지는 멱등키와 주소(paymentKey)로 본다. 같은 키에 다른 본문이 오면 어떻게 답하는지는 문서에 없어서,
 *   여기서는 본문을 보지 않고 첫 응답을 돌려준다고 가정한다. 첫 응답을 몇 일 동안 기억하는지(토스페이먼츠 15일)는 흉내 내지 않는다
 */
@RestController
@RequiredArgsConstructor
public class PaymentInquiryController {

    private final ScenarioStore scenarioStore;

    @GetMapping("/v1/payments/orders/{orderId}")
    public ResponseEntity<Object> findByOrderId(@PathVariable String orderId) throws InterruptedException {
        waitFollowUpDelay();
        return scenarioStore.findByOrderId(orderId)
                .<ResponseEntity<Object>>map(record -> ResponseEntity.ok(PaymentResponse.from(record)))
                .orElseGet(PaymentInquiryController::notFound);
    }

    @PostMapping("/v1/payments/{paymentKey}/cancel")
    public ResponseEntity<Object> cancel(@PathVariable String paymentKey,
                                         @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                         @RequestBody CancelRequest request) throws InterruptedException {
        PaymentRecord record = scenarioStore.findByPaymentKey(paymentKey).orElse(null);
        if (Objects.isNull(record)) {
            waitFollowUpDelay();
            return notFound();
        }
        if (Objects.isNull(idempotencyKey)) {
            return cancel(record.getOrderId(), request);
        }
        return cancelOnce(record.getOrderId(), paymentKey, idempotencyKey, request);
    }

    private ResponseEntity<Object> cancelOnce(String orderId, String paymentKey, String idempotencyKey, CancelRequest request)
            throws InterruptedException {
        ResponseEntity<Object> first = scenarioStore.idempotentResponse(paymentKey, idempotencyKey).orElse(null);
        if (Objects.nonNull(first)) {
            return first;
        }
        if (scenarioStore.startIdempotent(paymentKey, idempotencyKey)) {
            ResponseEntity<Object> response = cancel(orderId, request);
            scenarioStore.finishIdempotent(paymentKey, idempotencyKey, response);
            return response;
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("IDEMPOTENT_REQUEST_PROCESSING", "The previous idempotent request is still being processed."));
    }

    private ResponseEntity<Object> cancel(String orderId, CancelRequest request) throws InterruptedException {
        scenarioStore.enterCancel(orderId);
        try {
            waitFollowUpDelay();
            return answer(scenarioStore.cancel(orderId, request.cancelAmount(), request.cancelReason()));
        } finally {
            scenarioStore.leaveCancel(orderId);
        }
    }

    private static ResponseEntity<Object> answer(CancelAttempt attempt) {
        return switch (attempt.getOutcome()) {
            case CANCELED -> ResponseEntity.ok(PaymentResponse.from(attempt.getRecord()));
            case ALREADY_CANCELED -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("ALREADY_CANCELED_PAYMENT", "The payment has already been canceled."));
            case NOT_CANCELABLE_PAYMENT -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("NOT_CANCELABLE_PAYMENT", "This is a non-cancelable payment."));
            case NOT_CANCELABLE_AMOUNT -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("NOT_CANCELABLE_AMOUNT", "This is a non-cancelable amount."));
        };
    }

    private static ResponseEntity<Object> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND_PAYMENT", "Not found payment"));
    }

    private void waitFollowUpDelay() throws InterruptedException {
        Thread.sleep(scenarioStore.current().getFollowUpDelay().toMillis());
    }
}
