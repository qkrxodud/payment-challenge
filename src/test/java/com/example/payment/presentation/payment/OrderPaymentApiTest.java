package com.example.payment.presentation.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.InventoryServer;
import com.example.payment.InventoryServerConfiguration;
import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.Shop;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.PaymentOutboxRelay;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "payment.pg.read-timeout=500ms")
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InventoryServerConfiguration.class})
@DisplayName("결제 API — 상품을 담은 주문(8장)")
class OrderPaymentApiTest {

    static final BigDecimal PRICE = new BigDecimal("10000");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // 10장부터: 재고는 재고 서비스에 있다
    @Autowired
    InventoryServer inventory;

    @Autowired
    PaymentOutboxRelay paymentOutboxRelay;

    PaymentApi api;

    Shop shop;

    @BeforeEach
    void connect() {
        api = new PaymentApi(jsonMapper, port);
        shop = new Shop(jdbcTemplate, inventory, paymentOutboxRelay);
    }

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
    }

    @Test
    @DisplayName("상품을 담아 맞는 금액으로 준비하면 그 금액의 준비 응답을 받는다")
    void prepareOrder() {
        String memberId = shop.member(BigDecimal.ZERO);
        long productId = shop.product(PRICE, 5);

        PaymentApi.Answer answer = api.prepareOrder(newOrderId(), 20000, memberId, productId, 2, null);

        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.decimal("amount")).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("상품가로 계산한 금액과 다른 금액으로 준비하면 금액이 맞지 않는다고 400 으로 답한다")
    void differentAmountIsRefused() {
        String memberId = shop.member(BigDecimal.ZERO);
        long productId = shop.product(PRICE, 5);

        PaymentApi.Answer answer = api.prepareOrder(newOrderId(), 100, memberId, productId, 2, null);

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.field("code")).isEqualTo("AMOUNT_MISMATCH");
    }

    @Test
    @DisplayName("재고가 없는 상품의 승인 요청은 재고가 없다고 409 로 답한다")
    void outOfStockIsConflict() {
        String orderId = newOrderId();
        api.prepareOrder(orderId, 10000, shop.member(BigDecimal.ZERO), shop.product(PRICE, 0), 1, null);

        PaymentApi.Answer answer = api.confirm(orderId, 10000);

        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.field("code")).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    @DisplayName("쓸 수 없는 쿠폰을 담아 준비하면 409 로 답한다")
    void unavailableCouponIsConflict() {
        long productId = shop.product(PRICE, 5);
        Long othersCoupon = shop.coupon(shop.member(BigDecimal.ZERO), new BigDecimal("2000"));

        PaymentApi.Answer answer = api.prepareOrder(newOrderId(), 8000, shop.member(BigDecimal.ZERO), productId, 1,
                othersCoupon);

        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.field("code")).isEqualTo("COUPON_NOT_AVAILABLE");
    }

    private static String newOrderId() {
        return "order-" + UUID.randomUUID();
    }
}
