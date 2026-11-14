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

    @Builder(access = AccessLevel.PRIVATE)
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
        changeStatus(PaymentAction.AUTHENTICATE);
    }

    public void failAuthentication() {
        changeStatus(PaymentAction.FAIL_AUTHENTICATION);
    }

    public void approve() {
        changeStatus(PaymentAction.APPROVE);
    }

    public void failApproval() {
        changeStatus(PaymentAction.FAIL_APPROVAL);
    }

    public void expire() {
        changeStatus(PaymentAction.EXPIRE);
    }

    public void capture() {
        changeStatus(PaymentAction.CAPTURE);
    }

    public void cancel() {
        changeStatus(PaymentAction.CANCEL);
    }

    private void changeStatus(PaymentAction action) {
        this.status = status.next(action);
    }
}
