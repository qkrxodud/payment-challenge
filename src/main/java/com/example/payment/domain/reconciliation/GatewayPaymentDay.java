package com.example.payment.domain.reconciliation;

import com.example.payment.domain.payment.GatewayStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 12장 결정 1: 그날 카드사 거래 내역으로 본 결제 하나(같은 결제 키의 거래들). 승인 거래가 그날 있으면 승인 금액과 남은 금액을 알고,
 * 그날 취소 거래만 있으면(앞날에 승인한 결제) 마지막 거래의 상태만 안다
 */
@Getter
public class GatewayPaymentDay {

    private final String paymentKey;
    private final String orderId;
    // 마지막 거래(처리한 때 순) 뒤의 결제 상태
    private final GatewayStatus lastStatus;
    // 그날 승인 거래의 금액. 그날 승인 거래가 없으면 비어 있다
    private final BigDecimal approvedAmount;
    private final BigDecimal canceledAmount;

    @Builder(access = AccessLevel.PRIVATE)
    private GatewayPaymentDay(String paymentKey, String orderId, GatewayStatus lastStatus, BigDecimal approvedAmount,
                              BigDecimal canceledAmount) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.lastStatus = lastStatus;
        this.approvedAmount = approvedAmount;
        this.canceledAmount = canceledAmount;
    }

    // 처리한 때 순의 거래들을 결제 키마다 묶는다(처음 나온 순서를 지킨다)
    public static Map<String, GatewayPaymentDay> byPaymentKey(List<GatewayTransaction> transactions) {
        Map<String, List<GatewayTransaction>> grouped = transactions.stream()
                .collect(Collectors.groupingBy(GatewayTransaction::getPaymentKey, LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)));
        Map<String, GatewayPaymentDay> days = new LinkedHashMap<>();
        grouped.forEach((paymentKey, sameKey) -> days.put(paymentKey, of(sameKey)));
        return days;
    }

    private static GatewayPaymentDay of(List<GatewayTransaction> sameKey) {
        GatewayTransaction last = sameKey.get(sameKey.size() - 1);
        return GatewayPaymentDay.builder()
                .paymentKey(last.getPaymentKey())
                .orderId(last.getOrderId())
                .lastStatus(last.getStatus())
                .approvedAmount(sameKey.stream()
                        .filter(GatewayTransaction::isApproval)
                        .map(GatewayTransaction::getAmount)
                        .findFirst()
                        .orElse(null))
                .canceledAmount(sameKey.stream()
                        .filter(transaction -> transaction.getStatus() != GatewayStatus.DONE)
                        .map(GatewayTransaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    public boolean hasApprovalThatDay() {
        return Objects.nonNull(approvedAmount);
    }

    // 그날 승인 거래가 있을 때만 남은 금액을 안다
    public Optional<BigDecimal> balance() {
        return Optional.ofNullable(approvedAmount).map(approved -> approved.subtract(canceledAmount));
    }

    public SettlementStage stage() {
        return switch (lastStatus) {
            case DONE -> SettlementStage.APPROVED;
            case PARTIAL_CANCELED -> SettlementStage.PARTIALLY_CANCELED;
            case CANCELED -> SettlementStage.CANCELED;
            default -> SettlementStage.NO_APPROVAL;
        };
    }
}
