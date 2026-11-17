package com.example.payment.presentation.payment;

import com.example.payment.application.payment.PaymentCancelService;
import com.example.payment.application.payment.PaymentConfirmService;
import com.example.payment.application.payment.PaymentPrepareService;
import com.example.payment.application.payment.PaymentQueryService;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.presentation.payment.dto.CancelPaymentRequest;
import com.example.payment.presentation.payment.dto.CancelResponse;
import com.example.payment.presentation.payment.dto.ConfirmPaymentRequest;
import com.example.payment.presentation.payment.dto.PaymentResponse;
import com.example.payment.presentation.payment.dto.PreparePaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentPrepareService paymentPrepareService;
    private final PaymentConfirmService paymentConfirmService;
    private final PaymentQueryService paymentQueryService;
    private final PaymentCancelService paymentCancelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse prepare(@RequestBody PreparePaymentRequest request) {
        return PaymentResponse.from(paymentPrepareService.prepare(
                new PreparePaymentCommand(request.orderId(), request.amount())));
    }

    // 결제창 인증을 마친 고객이 돌아오면 PG 가 준 paymentKey 와 결제창에서 인증한 금액으로 승인을 요청한다
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<PaymentResponse> confirm(@PathVariable String orderId, @RequestBody ConfirmPaymentRequest request) {
        PaymentResult result = paymentConfirmService.confirm(
                new ConfirmPaymentCommand(orderId, request.paymentKey(), request.amount()));
        return ResponseEntity.status(statusOf(result)).body(PaymentResponse.from(result));
    }

    // 5장: 승인된 결제를 취소한다. 금액이 없으면 남은 금액을 모두 취소한다.
    // Idempotency-Key 가 없으면 서비스가 400 으로 거부한다(헤더가 없을 때도 같은 오류 응답 모양으로 답하려고 여기서 막지 않는다)
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CancelResponse> cancel(@PathVariable String orderId,
                                                 @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                                 @RequestBody CancelPaymentRequest request) {
        CancelResult result = paymentCancelService.cancel(
                new CancelPaymentCommand(orderId, idempotencyKey, request.cancelAmount(), request.cancelReason()));
        return ResponseEntity.status(statusOf(result)).body(CancelResponse.from(result));
    }

    @GetMapping("/{orderId}")
    public PaymentResponse find(@PathVariable String orderId) {
        return PaymentResponse.from(paymentQueryService.find(orderId));
    }

    // 결과를 모르는 결제에 다시 온 승인 요청: 카드사에 다시 묻지 않고 확인 중이라는 뜻으로 202 를 준다(4장).
    // 7장부터 카드사 답을 기다리는(인증 상태) 결제에 같은 결제 키로 다시 온 요청도 같은 뜻으로 202 를 준다.
    // 처음 요청이 시간 초과로 끝날 때는 504(PAYMENT_GATEWAY_NO_RESPONSE)를 그대로 준다
    private static HttpStatus statusOf(PaymentResult result) {
        if (result.status().isAwaitingApprovalResult()) {
            return HttpStatus.ACCEPTED;
        }
        return HttpStatus.OK;
    }

    // 결과를 모르는 취소에 같은 키로 다시 온 요청: 확인 중이라는 뜻으로 202 를 준다(5장). 처음 요청의 시간 초과는 504 그대로
    private static HttpStatus statusOf(CancelResult result) {
        if (result.cancelStatus().isAwaitingResult()) {
            return HttpStatus.ACCEPTED;
        }
        return HttpStatus.OK;
    }
}
