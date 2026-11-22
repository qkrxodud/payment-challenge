package com.example.payment.domain.reconciliation;

import com.example.payment.domain.payment.GatewayStatus;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 12장 결정 1: 대사에서 찾은 어긋남 한 건. 우리 쪽과 카드사 쪽 값을 그대로 함께 남긴다(한쪽에만 있으면 다른 쪽은 비어 있다).
 * 처리(resolution)는 찾은 때 운영자 확인으로 두고, 대사가 맞추면 맞춤으로 바꾼다(결정 2)
 */
@Getter
public class Mismatch {

    private final String paymentKey;
    private final String orderId;
    private final MismatchType type;
    private final PaymentStatus ourStatus;
    private final BigDecimal ourAmount;
    private final BigDecimal ourBalance;
    private final GatewayStatus gatewayStatus;
    private final BigDecimal gatewayAmount;
    private final BigDecimal gatewayBalance;
    private final Resolution resolution;

    @Builder(access = AccessLevel.PRIVATE, toBuilder = true)
    private Mismatch(String paymentKey, String orderId, MismatchType type, PaymentStatus ourStatus, BigDecimal ourAmount,
                     BigDecimal ourBalance, GatewayStatus gatewayStatus, BigDecimal gatewayAmount, BigDecimal gatewayBalance,
                     Resolution resolution) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.type = type;
        this.ourStatus = ourStatus;
        this.ourAmount = ourAmount;
        this.ourBalance = ourBalance;
        this.gatewayStatus = gatewayStatus;
        this.gatewayAmount = gatewayAmount;
        this.gatewayBalance = gatewayBalance;
        this.resolution = resolution;
    }

    // 우리 결제와 그날 카드사 결제를 견준다. 어느 한쪽이 없으면 비어 있다(null). 어긋나지 않으면 비어 있다
    public static Optional<Mismatch> between(OurPayment ours, GatewayPaymentDay gateway) {
        if (Objects.isNull(gateway)) {
            return ourOnly(ours);
        }
        if (Objects.isNull(ours)) {
            return Optional.of(of(MismatchType.PG_ONLY, null, gateway));
        }
        return Optional.ofNullable(typeOf(ours, gateway)).map(type -> of(type, ours, gateway));
    }

    // 카드사 거래 내역에 없는 우리 결제는 승인이 있었던 결제만 어긋남이다. 승인 결과를 기다리는 결제는 4장 보정이 본다
    private static Optional<Mismatch> ourOnly(OurPayment ours) {
        if (ours.stage().hadApproval()) {
            return Optional.of(of(MismatchType.OURS_ONLY, ours, null));
        }
        return Optional.empty();
    }

    // 금액 → 단계 → (같은 단계면) 남은 금액 순으로 견준다
    private static MismatchType typeOf(OurPayment ours, GatewayPaymentDay gateway) {
        if (gateway.hasApprovalThatDay() && ours.getAmount().compareTo(gateway.getApprovedAmount()) != 0) {
            return MismatchType.AMOUNT_MISMATCH;
        }
        MismatchType byStage = typeByStage(ours.stage(), gateway.stage());
        if (Objects.nonNull(byStage) || cannotCompareBalance(ours, gateway)) {
            return byStage;
        }
        return typeByBalance(ours.balance(), gateway.balance().orElseThrow());
    }

    // 같은 단계라도 둘 다 승인이 있었고 그날 승인 거래가 있으면 남은 금액까지 본다(부분 취소가 몇 번이었는지).
    // 그날 승인 거래가 없으면(앞날 승인) 카드사 남은 금액을 몰라 단계만 본다
    private static boolean cannotCompareBalance(OurPayment ours, GatewayPaymentDay gateway) {
        return gateway.balance().isEmpty() || ours.stage().hadNoApproval();
    }

    private static MismatchType typeByBalance(BigDecimal ourBalance, BigDecimal gatewayBalance) {
        int compared = ourBalance.compareTo(gatewayBalance);
        if (compared == 0) {
            return null;
        }
        if (compared > 0) {
            return MismatchType.STATUS_BEHIND;
        }
        return MismatchType.STATUS_MISMATCH;
    }

    private static MismatchType typeByStage(SettlementStage ours, SettlementStage gateway) {
        if (ours == gateway) {
            return null;
        }
        if (ours.isBehind(gateway)) {
            return MismatchType.STATUS_BEHIND;
        }
        return MismatchType.STATUS_MISMATCH;
    }

    private static Mismatch of(MismatchType type, OurPayment ours, GatewayPaymentDay gateway) {
        Optional<OurPayment> our = Optional.ofNullable(ours);
        Optional<GatewayPaymentDay> card = Optional.ofNullable(gateway);
        return Mismatch.builder()
                .paymentKey(our.map(OurPayment::getPaymentKey).orElseGet(() -> gateway.getPaymentKey()))
                // 카드사에만 있으면 카드사 쪽 값을 쓴다(지금 이 줄을 만들 때 gateway 는 비어 있지 않다)
                .orderId(our.map(OurPayment::getOrderId).orElseGet(() -> gateway.getOrderId()))
                .type(type)
                .ourStatus(our.map(OurPayment::getStatus).orElse(null))
                .ourAmount(our.map(OurPayment::getAmount).orElse(null))
                .ourBalance(our.map(OurPayment::balance).orElse(null))
                .gatewayStatus(card.map(GatewayPaymentDay::getLastStatus).orElse(null))
                .gatewayAmount(card.map(GatewayPaymentDay::getApprovedAmount).orElse(null))
                .gatewayBalance(card.flatMap(GatewayPaymentDay::balance).orElse(null))
                .resolution(Resolution.NEEDS_REVIEW)
                .build();
    }

    // 12장 결정 2: 우리 기록이 뒤처진 어긋남만 대사가 카드사를 따라 맞춘다
    public boolean isFollowable() {
        return type == MismatchType.STATUS_BEHIND;
    }

    public Mismatch followed() {
        return toBuilder().resolution(Resolution.FOLLOWED).build();
    }
}
