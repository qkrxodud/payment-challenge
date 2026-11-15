package com.example.mockpg;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 가짜 PG의 상태: 지금 시나리오, 주문별 승인 금액, 지금 응답을 기다리게 하고 있는 주문.
 * 상태를 두는 것 자체가 목적인 시험 도구라 스레드 안전 저장소만 쓴다.
 */
@Component
public class ScenarioStore {

    private final AtomicReference<Scenario> scenario = new AtomicReference<>(Scenario.immediateApproval());
    private final Map<String, List<BigDecimal>> approvals = new ConcurrentHashMap<>();
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
        inFlight.add(orderId);
    }

    public void leave(String orderId) {
        inFlight.remove(orderId);
    }

    public boolean isInFlight(String orderId) {
        return inFlight.contains(orderId);
    }

    public void recordApproval(String orderId, BigDecimal amount) {
        approvals.computeIfAbsent(orderId, key -> new CopyOnWriteArrayList<>()).add(amount);
    }

    public int approvalCount(String orderId) {
        return approvedAmounts(orderId).size();
    }

    // 승인한 순서대로의 금액. 요청에 금액이 없었으면 null 이 그대로 남는다
    public List<BigDecimal> approvedAmounts(String orderId) {
        return new ArrayList<>(approvals.getOrDefault(orderId, List.of()));
    }
}
