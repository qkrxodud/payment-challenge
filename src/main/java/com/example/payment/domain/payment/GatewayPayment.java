package com.example.payment.domain.payment;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 카드사에 지금 남아 있는 결제(6장): 결제 상태와 카드사가 끝낸 취소들. 웹훅을 받으면 이것을 다시 읽어 우리 결제를 맞춘다.
 * 4장 보정도 6장부터 이것으로 맞춘다. 카드사에 이 주문의 결제가 없으면 상태는 NOT_FOUND, 취소는 없다.
 */
@Getter
public class GatewayPayment {

    private final GatewayStatus status;
    @Getter(AccessLevel.NONE)
    private final List<GatewayCancel> cancels;

    @Builder(access = AccessLevel.PRIVATE)
    private GatewayPayment(GatewayStatus status, List<GatewayCancel> cancels) {
        this.status = status;
        this.cancels = List.copyOf(cancels);
    }

    public static GatewayPayment of(GatewayStatus status, List<GatewayCancel> cancels) {
        return GatewayPayment.builder()
                .status(status)
                .cancels(cancels)
                .build();
    }

    public static GatewayPayment notFound() {
        return of(GatewayStatus.NOT_FOUND, List.of());
    }

    public List<GatewayCancel> getCancels() {
        return List.copyOf(cancels);
    }
}
