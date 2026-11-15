package com.example.payment.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서버가 돌려주는 오류 코드의 단일 목록. clientMessage 는 응답에 그대로 나가는 영어 문장이다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 요청 본문을 읽을 수 없음(JSON 모양이 틀림, 값의 형식이 틀림)
    INVALID_INPUT(ErrorType.INVALID_INPUT, "The request could not be read."),
    // 웹 계층이 거절한 요청(지원하지 않는 HTTP 방식 등). 상태 코드는 그 거절의 것을 그대로 쓴다
    INVALID_REQUEST(ErrorType.INVALID_INPUT, "This request is not supported."),
    // 금액이 없거나 0 이하
    INVALID_AMOUNT(ErrorType.INVALID_INPUT, "The amount must be a positive number."),
    // 승인 요청 금액이 준비한 금액과 다름. 결제 상태는 바꾸지 않는다
    AMOUNT_MISMATCH(ErrorType.INVALID_INPUT, "The amount does not match the prepared payment."),
    PAYMENT_NOT_FOUND(ErrorType.NOT_FOUND, "No payment was found for this order."),
    // 같은 주문번호가 다른 금액으로 이미 준비됨
    DUPLICATE_ORDER(ErrorType.CONFLICT, "This order was already prepared with a different amount."),
    // 지금 결제 상태에서는 할 수 없는 요청(승인 전 매입, 취소 후 재승인 등)
    INVALID_PAYMENT_TRANSITION(ErrorType.CONFLICT, "This payment cannot be changed in its current status."),
    // PG 가 승인도 거절도 아닌 답(5xx 등)을 줬다
    PAYMENT_GATEWAY_FAILED(ErrorType.UPSTREAM_FAILED, "The card company could not process this payment."),
    // PG 의 답을 정해진 시간 안에 받지 못했다. 승인됐는지 알 수 없다
    PAYMENT_GATEWAY_NO_RESPONSE(ErrorType.UPSTREAM_NO_RESPONSE,
            "The card company did not answer in time. Check the payment status before trying again."),
    INTERNAL_ERROR(ErrorType.INTERNAL, "Something went wrong on the server.");

    private final ErrorType type;
    private final String clientMessage;
}
