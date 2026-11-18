package com.example.payment.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 8장: 파는 상품. 결제 금액은 이 상품가로 서버가 정한다. 재고는 상품마다 한 줄(stock)로 따로 둔다 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Builder(access = AccessLevel.PRIVATE)
    private Product(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    public static Product of(String name, BigDecimal price) {
        return Product.builder()
                .name(name)
                .price(price)
                .build();
    }
}
