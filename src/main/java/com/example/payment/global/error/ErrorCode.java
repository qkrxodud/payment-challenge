package com.example.payment.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서버가 돌려주는 오류 코드의 단일 목록. clientMessage 는 응답에 그대로 나가는 영어 문장이다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 지금 결제 상태에서는 할 수 없는 요청(승인 전 매입, 취소 후 재승인 등)
    INVALID_PAYMENT_TRANSITION(ErrorType.CONFLICT, "This payment cannot be changed in its current status.");

    private final ErrorType type;
    private final String clientMessage;
}
