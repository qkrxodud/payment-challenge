package com.example.payment.application.payment;

import com.example.payment.Attempt;
import com.example.payment.InventoryServer;
import com.example.payment.MockPgServer;
import com.example.payment.Shop;
import com.example.payment.SlowWrites;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.OrderLine;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 8장 계약 테스트들이 함께 쓰는 주문 도우미. 시험마다 새 상품·회원·쿠폰을 차리고(Shop), 상품을 담아 준비하고 승인을 요청한다.
 * 상품가는 10000원이다. 준비·승인 진입점은 운영 서비스이고, 비교 구현은 prepare·confirm 을 바꿔 끼운다.
 * 10장부터 재고는 시험 JVM 안에 띄운 재고 서비스(InventoryServer)에 있다.
 */
abstract class OrderPaymentContract {

    static final BigDecimal PRICE = new BigDecimal("10000");

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentQueryService paymentQueryService;

    // 10장부터: 재고는 재고 서비스에 있다
    @Autowired
    InventoryServer inventory;

    @Autowired
    PaymentOutboxRelay paymentOutboxRelay;

    Shop shop;

    SlowWrites slowWrites;

    @BeforeEach
    void openShop() {
        shop = new Shop(jdbcTemplate, inventory, paymentOutboxRelay);
        slowWrites = new SlowWrites(jdbcTemplate);
    }

    @AfterEach
    void closeShop() {
        slowWrites.release();
        mockPg.reset();
        inventory.reset();
    }

    PaymentResult prepare(PreparePaymentCommand command) {
        return paymentPrepareService.prepare(command);
    }

    PaymentResult confirm(ConfirmPaymentCommand command) {
        return paymentConfirmService.confirm(command);
    }

    // 상품 하나를 이 수량만큼 담은 주문. 금액은 고객이 보낸 금액이다
    static PreparePaymentCommand order(String memberId, Long productId, int quantity, Long couponId, BigDecimal usePoint,
                                       BigDecimal amount) {
        return new PreparePaymentCommand("order-" + UUID.randomUUID(), amount, memberId,
                List.of(new OrderLine(productId, quantity)), couponId, usePoint);
    }

    // 쿠폰·포인트 없이 상품만 담은 주문. 금액은 상품가 × 수량으로 맞게 보낸다
    static PreparePaymentCommand order(Long productId, int quantity) {
        return order(null, productId, quantity, null, null, PRICE.multiply(BigDecimal.valueOf(quantity)));
    }

    // 준비한 결제에 그 금액으로 승인을 요청한다
    PaymentResult confirm(PaymentResult prepared) {
        return confirm(new ConfirmPaymentCommand(prepared.orderId(), keyOf(prepared.orderId()), prepared.amount()));
    }

    // 준비하고 이어서 승인을 요청한다. 준비나 승인에서 받지 않으면 그 이유가 담긴다
    Attempt<PaymentResult> orderAndConfirm(PreparePaymentCommand command) {
        Attempt<PaymentResult> prepared = Attempt.of(() -> prepare(command));
        if (prepared.isAnswered()) {
            return Attempt.of(() -> confirm(prepared.result()));
        }
        return prepared;
    }

    static String keyOf(String orderId) {
        return "key-" + orderId;
    }
}
