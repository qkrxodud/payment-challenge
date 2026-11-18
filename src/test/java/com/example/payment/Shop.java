package com.example.payment;

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
 */
public class Shop {

    private final JdbcTemplate jdbcTemplate;

    public Shop(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 이 값의 상품과 그 재고를 남기고 상품 번호를 돌려준다
    public Long product(BigDecimal price, int stock) {
        Long productId = insert("insert into product (name, price) values (?, ?)", "product-" + UUID.randomUUID(), price);
        jdbcTemplate.update("insert into stock (product_id, quantity) values (?, ?)", productId, stock);
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
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "select quantity from stock where product_id = ?", Integer.class, productId));
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
