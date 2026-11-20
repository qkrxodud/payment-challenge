package com.example.payment.application.payment.comparison;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 비교 구현(10장 결정 1, 승인 먼저): 재고 서비스의 답을 처리한 주문과 재고를 줄인 주문. 같은 시험 JVM 의 결제 서버들(OtherPaymentServer)이
 * 함께 쓰도록 정적으로 둔다(주문번호는 시험마다 새로 만든다).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StockReplies {

    private static final Set<String> REPLIED = ConcurrentHashMap.newKeySet();
    private static final Set<String> DECREASED = ConcurrentHashMap.newKeySet();

    static void decreased(String orderId) {
        DECREASED.add(orderId);
    }

    static void replied(String orderId) {
        REPLIED.add(orderId);
    }

    public static boolean isDecreased(String orderId) {
        return DECREASED.contains(orderId);
    }

    public static boolean isReplied(String orderId) {
        return REPLIED.contains(orderId);
    }
}
