package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

/** 10장: 재고 서비스가 업무 답이 아닌 답(5xx 등)을 줬다 */
public class InventoryFailedException extends PaymentChallengeException {

    public InventoryFailedException(String target, int status) {
        super(ErrorCode.INVENTORY_FAILED, "The inventory answered " + status + " for " + target + ".");
    }
}
