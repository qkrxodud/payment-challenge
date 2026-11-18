package com.example.payment.domain.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 8장: 결제에 담긴 상품 한 줄. 결제(Payment)에 딸린 기록이라 결제를 거쳐서만 만든다.
 * 준비할 때의 상품가를 함께 남긴다 — 나중에 상품가가 바뀌어도 이 결제의 금액과 되돌릴 수량은 그대로다.
 */
@Entity
@Table(name = "payment_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentItem(Long productId, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public static PaymentItem of(Long productId, int quantity, BigDecimal unitPrice) {
        return PaymentItem.builder()
                .productId(productId)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
    }

    // 이 줄의 상품가 합(상품가 × 수량)
    public BigDecimal totalPrice() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    void belongTo(Payment owner) {
        this.payment = owner;
    }
}
