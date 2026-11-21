package com.example.order.domain.error.exception;

import com.example.order.domain.error.ErrorCode;
import com.example.order.domain.error.OrderServiceException;

/** 11장 결정 3: 고유 번호나 주문번호가 없거나, 고유 번호가 숫자가 아닌 이벤트. 다시 시도하지 않는다 */
public class InvalidPaymentEventException extends OrderServiceException {

    public InvalidPaymentEventException() {
        super(ErrorCode.INVALID_PAYMENT_EVENT, "A payment event without a valid event number or order id was received.");
    }
}
