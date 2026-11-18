package com.example.payment.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 8장: 회원에게 발급한 정액 할인 쿠폰. 결제 하나에만 쓸 수 있다.
 * 쓰고 되돌리는 것은 쿠폰 줄을 조건으로 바꾸는 저장소가 한다(CouponRepository) — 여러 주문이 같은 쿠폰을 동시에 쓰려 할 수 있다.
 */
@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponStatus status;

    // 이 쿠폰을 쓴 결제
    @Column(name = "payment_id")
    private Long paymentId;

    @Builder(access = AccessLevel.PRIVATE)
    private Coupon(String memberId, BigDecimal discountAmount, CouponStatus status) {
        this.memberId = memberId;
        this.discountAmount = discountAmount;
        this.status = status;
    }

    public static Coupon issue(String memberId, BigDecimal discountAmount) {
        return Coupon.builder()
                .memberId(memberId)
                .discountAmount(discountAmount)
                .status(CouponStatus.ISSUED)
                .build();
    }

    // 이 회원이 지금 쓸 수 있는 쿠폰인가(이 회원에게 발급됐고 아직 쓰지 않음)
    public boolean isUsableBy(String requestedMemberId) {
        return status.isIssued() && Objects.equals(memberId, requestedMemberId);
    }
}
