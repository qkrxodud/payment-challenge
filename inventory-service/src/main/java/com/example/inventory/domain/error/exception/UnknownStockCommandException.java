package com.example.inventory.domain.error.exception;

import com.example.inventory.domain.error.ErrorCode;
import com.example.inventory.domain.error.InventoryException;

/** 11장 결정 3: 확정(CONFIRM)도 해제(RELEASE)도 아닌 재고 명령. 예약을 바꾸지 않고, 다시 시도하지 않는다 */
public class UnknownStockCommandException extends InventoryException {

    public UnknownStockCommandException(String commandId) {
        super(ErrorCode.UNKNOWN_STOCK_COMMAND, "Stock command " + commandId + " is neither a confirm nor a release.");
    }
}
