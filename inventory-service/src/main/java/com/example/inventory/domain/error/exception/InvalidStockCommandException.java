package com.example.inventory.domain.error.exception;

import com.example.inventory.domain.error.ErrorCode;
import com.example.inventory.domain.error.InventoryException;

/** 11장 결정 3: 명령 번호나 예약 번호가 없거나, 명령 번호가 숫자가 아닌 재고 명령. 다시 시도하지 않는다 */
public class InvalidStockCommandException extends InventoryException {

    public InvalidStockCommandException() {
        super(ErrorCode.INVALID_STOCK_COMMAND, "A stock command without a valid command number or reservation id was received.");
    }
}
