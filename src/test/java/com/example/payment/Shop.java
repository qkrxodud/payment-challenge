package com.example.payment;

import com.example.payment.application.payment.PaymentOutboxRelay;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * 8장: 시험마다 새 상품·재고, 포인트를 가진 회원, 쿠폰을 차려 두고, 시험 뒤의 재고·포인트·쿠폰을 읽는 시험 도구.
 * 상품·쿠폰을 등록하는 API 는 이 연재의 범위 밖이라 시험이 직접 남긴다.
 * 10장부터 재고는 재고 서비스의 DB 에 남기고 거기서 읽는다(상품은 결제 서버에 그대로). 재고를 읽기 전에 결제 서버가 남긴 결과를 모두 보내고
 * (9장 릴레이) 재고 서비스가 받은 것을 다 처리할 때까지 기다린다 — 결제가 끝난 뒤의 재고를 읽는다.
 */
public class Shop {

    // 한 번에 보내는 결과는 릴레이 한 주기만큼(50건)이라, 앞 시험이 남긴 결과까지 다 보낼 때까지 몇 번 돌린다
    private static final int MAX_RELAY_ROUNDS = 100;

    private final JdbcTemplate jdbcTemplate;
    private final InventoryServer inventory;
    private final PaymentOutboxRelay paymentOutboxRelay;

    public Shop(JdbcTemplate jdbcTemplate, InventoryServer inventory, PaymentOutboxRelay paymentOutboxRelay) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventory = inventory;
        this.paymentOutboxRelay = paymentOutboxRelay;
    }

    // 이 값의 상품과 그 재고를 남기고 상품 번호를 돌려준다
    public Long product(BigDecimal price, int stock) {
        Long productId = insert("insert into product (name, price) values (?, ?)", "product-" + UUID.randomUUID(), price);
        inventory.stock(productId, stock);
        return productId;
    }

    // 포인트를 이만큼 가진 회원
    public String member(BigDecimal points) {
        String memberId = "member-" + UUID.randomUUID();
        jdbcTemplate.update("insert into point_wallet (member_id, balance) values (?, ?)", memberId, points);
        return memberId;
    }

    // 이 회원에게 정액 할인 쿠폰을 발급한다
    public Long coupon(String memberId, BigDecimal discount) {
        return insert("insert into coupon (member_id, discount_amount, status) values (?, ?, 'ISSUED')", memberId, discount);
    }

    public int stockOf(Long productId) {
        settle();
        return inventory.stockOf(productId);
    }

    // 10장: 확정된 예약이 이 상품을 잡은 수량
    public int confirmedQuantityOf(Long productId) {
        settle();
        return inventory.confirmedQuantityOf(productId);
    }

    // 결제 서버가 남긴 결과를 모두 보내고, 재고 서비스가 받은 것을 다 처리할 때까지 기다린다
    public void settle() {
        for (int round = 0; round < MAX_RELAY_ROUNDS && hasUnsent(); round++) {
            paymentOutboxRelay.relay();
        }
        inventory.awaitCaughtUp();
    }

    private boolean hasUnsent() {
        Integer unsent = jdbcTemplate.queryForObject("select count(*) from payment_outbox where sent_at is null",
                Integer.class);
        return Objects.requireNonNullElse(unsent, 0) > 0;
    }

    public BigDecimal pointsOf(String memberId) {
        return jdbcTemplate.queryForObject("select balance from point_wallet where member_id = ?", BigDecimal.class,
                memberId);
    }

    public String couponStatusOf(Long couponId) {
        return jdbcTemplate.queryForObject("select status from coupon where id = ?", String.class, couponId);
    }

    private Long insert(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }
}
