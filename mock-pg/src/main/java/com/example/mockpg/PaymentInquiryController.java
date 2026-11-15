package com.example.mockpg;

import com.example.mockpg.dto.CancelRequest;
import com.example.mockpg.dto.ErrorResponse;
import com.example.mockpg.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4장부터: 승인 뒤에 오는 결제 조회와 취소. 경로와 오류 코드는 토스페이먼츠 API 를 따른다.
 * 토스페이먼츠는 주문번호 조회를 "승인된 결제"의 조회로 설명한다. 여기서는 거절(ABORTED)도 조회로 돌려준다고 가정한다.
 * 둘 다 시나리오의 followUpDelay 만큼 늦게 답한다.
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
    public ResponseEntity<Object> cancel(@PathVariable String paymentKey, @RequestBody CancelRequest request)
            throws InterruptedException {
        waitFollowUpDelay();
        return scenarioStore.findByPaymentKey(paymentKey)
                .map(this::cancel)
                .orElseGet(PaymentInquiryController::notFound);
    }

    private ResponseEntity<Object> cancel(PaymentRecord record) {
        if (record.getState().isCanceled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("ALREADY_CANCELED_PAYMENT", "The payment has already been canceled."));
        }
        PaymentRecord result = scenarioStore.cancel(record);
        if (result.getState().isCanceled()) {
            return ResponseEntity.ok(PaymentResponse.from(result));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("NOT_CANCELABLE_PAYMENT", "This is a non-cancelable payment."));
    }

    private static ResponseEntity<Object> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND_PAYMENT", "Not found payment"));
    }

    private void waitFollowUpDelay() throws InterruptedException {
        Thread.sleep(scenarioStore.current().getFollowUpDelay().toMillis());
    }
}
