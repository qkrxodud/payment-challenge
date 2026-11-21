package com.example.order.domain.error.exception;

import com.example.order.domain.error.ErrorCode;
import com.example.order.domain.error.OrderServiceException;

/** 11장 결정 3: 모르는 결제 상태가 실린 이벤트. 다시 시도하지 않는다 */
public class UnknownPaymentStatusException extends OrderServiceException {

    public UnknownPaymentStatusException(String eventId) {
        super(ErrorCode.UNKNOWN_PAYMENT_STATUS, "Payment event " + eventId + " has an unknown payment status.");
    }
}
