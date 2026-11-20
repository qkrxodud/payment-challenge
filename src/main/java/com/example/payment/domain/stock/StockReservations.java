package com.example.payment.domain.stock;

import com.example.payment.domain.payment.PaymentItem;
import java.util.List;

/**
 * 10장: 재고 서비스에 있는 재고. 결제 하나에 담긴 상품의 재고를 한꺼번에 잡는다.
 * 10장 결정 1: 카드사에 승인을 요청하기 전에 잡는다(답을 기다린다). 답을 정해진 시간 안에 받지 못하면 InventoryNoResponseException 으로 알린다.
 * 10장 결정 3: 결제 번호를 예약 번호로 잡고(예약), 결제가 승인되면 확정하고, 승인 없이 끝나거나 전액 취소되면 해제한다.
 * 담긴 상품은 해제할 때도 함께 넘긴다 — 예약 기록을 쓰는 재고 서비스는 쓰지 않는다(수량만 바꾸는 비교 구현이 쓴다).
 */
public interface StockReservations {

    // 담긴 상품의 재고가 모두 있으면 잡고 맞다고 답한다. 하나라도 모자라거나 이미 해제된 예약 번호면 잡지 않고 아니라고 답한다
    boolean reserve(Long reservationId, String orderId, List<PaymentItem> items);

    void confirm(Long reservationId, String orderId);

    void release(Long reservationId, String orderId, List<PaymentItem> items);
}
