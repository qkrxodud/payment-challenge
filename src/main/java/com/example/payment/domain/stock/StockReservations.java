package com.example.payment.domain.stock;

import com.example.payment.domain.payment.PaymentItem;
import java.util.List;

/**
 * 10장: 재고 서비스에 있는 재고. 결제 하나에 담긴 상품의 재고를 한꺼번에 잡고 돌려준다.
 * 10장 결정 1: 카드사에 승인을 요청하기 전에 잡는다(답을 기다린다). 답을 정해진 시간 안에 받지 못하면 InventoryNoResponseException 으로 알린다.
 */
public interface StockReservations {

    // 담긴 상품의 재고가 모두 있으면 잡고 맞다고 답한다. 하나라도 모자라면 아무것도 잡지 않고 아니라고 답한다
    boolean reserve(String orderId, List<PaymentItem> items);

    // 잡아 둔 재고를 돌려준다
    void release(String orderId, List<PaymentItem> items);
}
