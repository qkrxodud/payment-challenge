package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.ApproveFirstConfirmService;
import com.example.payment.application.payment.comparison.ApproveFirstHoldings;
import com.example.payment.application.payment.comparison.ApproveFirstReplyListener;
import com.example.payment.application.payment.comparison.QuantityStockCommandSender;
import com.example.payment.application.payment.comparison.QuantityStockReservations;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** 결제 응답은 재고 서비스의 답을 기다리지 않는다(계약 시험과 달리 답을 기다리지 않고 다음 주문을 보낸다) */
@Import({ApproveFirstHoldings.class, ApproveFirstConfirmService.class, ApproveFirstReplyListener.class,
        QuantityStockReservations.class, QuantityStockCommandSender.class})
@TestPropertySource(properties = "test.inventory.components="
        + "com.example.payment.application.payment.comparison.ApproveFirstStockListener,"
        + "com.example.payment.application.payment.comparison.QuantityStockRecorder,"
        + "com.example.inventorycomparison.QuantityStockController,"
        + "com.example.payment.application.payment.comparison.QuantityStockCommandListener")
class ApproveFirstSagaMeasurement extends SagaMeasurement {

    @Autowired
    ApproveFirstConfirmService service;

    @Override
    String name() {
        return "결정 1 비교: 승인 먼저(Kafka 차감, 보상 취소)";
    }

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }
}
