package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.QuantityStockCommandSender;
import com.example.payment.application.payment.comparison.QuantityStockReservations;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import({QuantityStockReservations.class, QuantityStockCommandSender.class})
@TestPropertySource(properties = "test.inventory.components="
        + "com.example.inventorycomparison.QuantityStockController,"
        + "com.example.payment.application.payment.comparison.QuantityStockRecorder,"
        + "com.example.payment.application.payment.comparison.QuantityStockCommandListener")
class QuantityOnlySagaMeasurement extends SagaMeasurement {

    @Override
    String name() {
        return "결정 3 비교: 수량만";
    }
}
