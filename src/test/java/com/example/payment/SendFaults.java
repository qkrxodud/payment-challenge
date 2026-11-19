package com.example.payment;

import com.example.payment.domain.payment.PaymentEvent;
import com.example.payment.global.error.exception.PaymentEventNotSentException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 9장: 릴레이가 이벤트를 보내는 순간을 실행마다 같게 고정하는 시험 도구(7장 SlowWrites 와 같은 생각).
 * 정한 결제 결과(주문번호 + 상태)의 첫 보내기 한 번만 붙잡거나(delay — 브로커에 늦게 닿는 보내기), 실패시키거나(fail — 브로커가 받지 못함),
 * 보내려던 릴레이를 죽인다(crash).
 * 같은 시험 JVM 에 뜬 결제 서버들(OtherPaymentServer)이 함께 쓰도록 상태를 정적으로 둔다 — 어느 서버든 먼저 보내는 쪽이 붙잡힌다.
 * FaultInjectingEventSender 가 보내기 전에 부른다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SendFaults {

    private static final Map<String, Duration> DELAYS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FAILURES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> CRASHES = new ConcurrentHashMap<>();

    public static void delayFirstSend(String orderId, String status, Duration delay) {
        DELAYS.put(keyOf(orderId, status), delay);
    }

    public static void failFirstSend(String orderId, String status) {
        FAILURES.put(keyOf(orderId, status), Boolean.TRUE);
    }

    // 보내려던 릴레이가 그 자리에서 죽는다(브로커에 닿지 않고, 릴레이도 그 주기를 더 돌지 않는다)
    public static void crashOnFirstSend(String orderId, String status) {
        CRASHES.put(keyOf(orderId, status), Boolean.TRUE);
    }

    public static void clear() {
        DELAYS.clear();
        FAILURES.clear();
        CRASHES.clear();
    }

    static void beforeSend(PaymentEvent event) {
        String key = keyOf(event.getOrderId(), event.getStatus().name());
        Duration delay = DELAYS.remove(key);
        if (Objects.nonNull(delay)) {
            sleep(delay);
        }
        if (Objects.nonNull(FAILURES.remove(key))) {
            throw new PaymentEventNotSentException(event.getEventId(), "failed for test");
        }
        if (Objects.nonNull(CRASHES.remove(key))) {
            throw new IllegalStateException("The relay died for test.");
        }
    }

    private static String keyOf(String orderId, String status) {
        return orderId + "/" + status;
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
