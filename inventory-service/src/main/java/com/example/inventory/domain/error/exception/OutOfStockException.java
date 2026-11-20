package com.example.inventory.domain.error.exception;

import com.example.inventory.domain.error.ErrorCode;
import com.example.inventory.domain.error.InventoryException;

public class OutOfStockException extends InventoryException {

    public OutOfStockException(Long productId) {
        super(ErrorCode.OUT_OF_STOCK, "Product " + productId + " is out of stock.");
    }
}
