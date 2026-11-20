package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

/** 10장: 재고 서비스의 답을 정해진 시간 안에 받지 못했다(시간 초과·연결 실패). 재고가 줄었는지 알 수 없다 */
public class InventoryNoResponseException extends PaymentChallengeException {

    public InventoryNoResponseException(String orderId) {
        super(ErrorCode.INVENTORY_NO_RESPONSE, "The inventory did not answer for order " + orderId + ".");
    }
}
