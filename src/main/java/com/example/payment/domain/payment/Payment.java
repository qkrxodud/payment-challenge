package com.example.payment.domain.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false, length = 64)
    private String orderId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Builder
    private Payment(String orderId, BigDecimal amount, PaymentStatus status) {
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
    }

    public static Payment create(String orderId, BigDecimal amount) {
        return Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .status(PaymentStatus.READY)
                .build();
    }

    public void authenticate() {
        this.status = PaymentStatus.AUTHENTICATED;
    }

    public void failAuthentication() {
        this.status = PaymentStatus.AUTH_FAILED;
    }

    public void approve() {
        this.status = PaymentStatus.APPROVED;
    }

    public void failApproval() {
        this.status = PaymentStatus.APPROVAL_FAILED;
    }

    public void expire() {
        this.status = PaymentStatus.EXPIRED;
    }

    public void capture() {
        this.status = PaymentStatus.CAPTURED;
    }

    public void cancel() {
        this.status = PaymentStatus.APPROVAL_CANCELED;
    }
}
