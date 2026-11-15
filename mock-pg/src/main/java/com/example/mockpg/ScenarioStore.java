package com.example.mockpg;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 가짜 PG의 상태: 지금 시나리오, 주문별 승인 금액과 결제 기록, 승인 요청·취소 횟수, 지금 처리 중인 주문.
 * 상태를 두는 것 자체가 목적인 시험 도구라 스레드 안전 저장소만 쓴다.
 */
@Component
public class ScenarioStore {

    private final AtomicReference<Scenario> scenario = new AtomicReference<>(Scenario.immediateApproval());
    private final Map<String, List<BigDecimal>> approvals = new ConcurrentHashMap<>();
    private final Map<String, PaymentRecord> payments = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> cancels = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> confirmRequests = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public Scenario current() {
        return scenario.get();
    }

    public void change(Scenario next) {
        scenario.set(next);
    }

    public void reset() {
        scenario.set(Scenario.immediateApproval());
    }

    public void enter(String orderId) {
        confirmRequests.computeIfAbsent(orderId, key -> new AtomicInteger()).incrementAndGet();
        inFlight.add(orderId);
    }

    public void leave(String orderId) {
        inFlight.remove(orderId);
    }

    public boolean isInFlight(String orderId) {
        return inFlight.contains(orderId);
    }

    public void recordApproval(String paymentKey, String orderId, BigDecimal amount) {
        approvals.computeIfAbsent(orderId, key -> new CopyOnWriteArrayList<>()).add(amount);
        payments.put(orderId, PaymentRecord.of(paymentKey, orderId, amount, PaymentState.DONE));
    }

    // 4장부터: 거절도 기록해 두어 나중에 조회로 알 수 있게 한다
    public void recordDecline(String paymentKey, String orderId, BigDecimal amount) {
        payments.put(orderId, PaymentRecord.of(paymentKey, orderId, amount, PaymentState.ABORTED));
    }

    public Optional<PaymentRecord> findByOrderId(String orderId) {
        return Optional.ofNullable(payments.get(orderId));
    }

    public Optional<PaymentRecord> findByPaymentKey(String paymentKey) {
        return payments.values().stream()
                .filter(record -> record.getPaymentKey().equals(paymentKey))
                .findFirst();
    }

    // 승인된 결제만 취소로 바꾼다. 바꿨으면 취소된 기록을, 바꿀 수 없었으면 지금 기록을 돌려준다
    public PaymentRecord cancel(PaymentRecord record) {
        PaymentRecord result = payments.computeIfPresent(record.getOrderId(), (orderId, current) -> cancelIfDone(current));
        return Optional.ofNullable(result).orElse(record);
    }

    private PaymentRecord cancelIfDone(PaymentRecord current) {
        if (current.getState().isDone()) {
            cancels.computeIfAbsent(current.getOrderId(), key -> new AtomicInteger()).incrementAndGet();
            return current.canceled();
        }
        return current;
    }

    public int approvalCount(String orderId) {
        return approvedAmounts(orderId).size();
    }

    // 승인한 순서대로의 금액. 요청에 금액이 없었으면 null 이 그대로 남는다
    public List<BigDecimal> approvedAmounts(String orderId) {
        return new ArrayList<>(approvals.getOrDefault(orderId, List.of()));
    }

    // 결과와 상관없이 승인 요청을 받은 횟수
    public int confirmRequestCount(String orderId) {
        return Optional.ofNullable(confirmRequests.get(orderId)).map(AtomicInteger::get).orElse(0);
    }

    public int cancelCount(String orderId) {
        return Optional.ofNullable(cancels.get(orderId)).map(AtomicInteger::get).orElse(0);
    }
}
