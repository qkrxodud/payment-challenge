package com.example.mockpg;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 가짜 PG의 상태: 지금 시나리오, 주문별 승인 횟수, 지금 응답을 기다리게 하고 있는 주문.
 * 상태를 두는 것 자체가 목적인 시험 도구라 스레드 안전 저장소만 쓴다.
 */
@Component
public class ScenarioStore {

    private final AtomicReference<Scenario> scenario = new AtomicReference<>(Scenario.immediateApproval());
    private final Map<String, Integer> approvals = new ConcurrentHashMap<>();
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

    public void recordApproval(String orderId) {
        approvals.merge(orderId, 1, Integer::sum);
    }

    public int approvalCount(String orderId) {
        return approvals.getOrDefault(orderId, 0);
    }
}
