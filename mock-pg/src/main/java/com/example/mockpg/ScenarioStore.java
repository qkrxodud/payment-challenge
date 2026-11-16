package com.example.mockpg;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 가짜 PG의 상태: 지금 시나리오, 주문별 승인 금액과 결제 기록, 승인 요청·취소 횟수, 지금 처리 중인 주문.
 * 5장부터 취소 요청을 받은 횟수, 멱등키별 첫 응답, 지금 처리 중인 멱등키를 더한다.
 * 6장부터 승인을 기록한 뒤 답을 붙잡아 두는 문(시험이 열 때까지 승인 답을 보내지 않음)을 더한다.
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
    private final Map<String, AtomicInteger> cancelRequests = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> cancelsInFlight = new ConcurrentHashMap<>();
    private final Map<String, ResponseEntity<Object>> idempotentResponses = new ConcurrentHashMap<>();
    private final Set<String> idempotentInFlight = ConcurrentHashMap.newKeySet();
    private final AtomicReference<CountDownLatch> confirmAnswerGate = new AtomicReference<>();

    public Scenario current() {
        return scenario.get();
    }

    public void change(Scenario next) {
        scenario.set(next);
    }

    public void reset() {
        scenario.set(Scenario.immediateApproval());
        releaseConfirmAnswers();
    }

    // 6장: 승인은 기록하되 그 답은 시험이 문을 열 때까지 보내지 않는다(알림이 승인 답보다 먼저 닿는 경우를 만든다)
    public void holdConfirmAnswers() {
        confirmAnswerGate.set(new CountDownLatch(1));
    }

    public void releaseConfirmAnswers() {
        CountDownLatch gate = confirmAnswerGate.getAndSet(null);
        if (Objects.nonNull(gate)) {
            gate.countDown();
        }
    }

    // 문이 닫혀 있으면 열릴 때까지(최대 10초) 기다린다
    public void awaitConfirmAnswer() throws InterruptedException {
        CountDownLatch gate = confirmAnswerGate.get();
        if (Objects.nonNull(gate)) {
            gate.await(10, TimeUnit.SECONDS);
        }
    }

    public void enter(String orderId) {
        confirmRequests.computeIfAbsent(orderId, key -> new AtomicInteger()).incrementAndGet();
        inFlight.add(orderId);
    }

    public void leave(String orderId) {
        inFlight.remove(orderId);
    }

    // 승인이나 취소를 처리하는 중인 주문
    public boolean isInFlight(String orderId) {
        return inFlight.contains(orderId)
                || Optional.ofNullable(cancelsInFlight.get(orderId)).map(AtomicInteger::get).orElse(0) > 0;
    }

    // 5장부터: 취소 요청을 받았다. 같은 멱등키로 다시 와서 처음 응답을 돌려준 요청도 센다. 결제를 찾을 수 없는 요청은 세지 않는다
    public void countCancelRequest(String orderId) {
        cancelRequests.computeIfAbsent(orderId, key -> new AtomicInteger()).incrementAndGet();
    }

    // 취소를 처리하기 시작한다(처리를 마칠 때까지 처리 중으로 본다)
    public void enterCancel(String orderId) {
        cancelsInFlight.computeIfAbsent(orderId, key -> new AtomicInteger()).incrementAndGet();
    }

    public void leaveCancel(String orderId) {
        cancelsInFlight.computeIfPresent(orderId, (key, count) -> {
            count.decrementAndGet();
            return count;
        });
    }

    // 6장부터 남긴 기록을 돌려준다(상태가 바뀐 결제를 웹훅으로 알린다)
    public PaymentRecord recordApproval(String paymentKey, String orderId, BigDecimal amount) {
        approvals.computeIfAbsent(orderId, key -> new CopyOnWriteArrayList<>()).add(amount);
        PaymentRecord record = PaymentRecord.of(paymentKey, orderId, amount, PaymentState.DONE);
        payments.put(orderId, record);
        return record;
    }

    // 4장부터: 거절도 기록해 두어 나중에 조회로 알 수 있게 한다
    public PaymentRecord recordDecline(String paymentKey, String orderId, BigDecimal amount) {
        PaymentRecord record = PaymentRecord.of(paymentKey, orderId, amount, PaymentState.ABORTED);
        payments.put(orderId, record);
        return record;
    }

    public Optional<PaymentRecord> findByOrderId(String orderId) {
        return Optional.ofNullable(payments.get(orderId));
    }

    public Optional<PaymentRecord> findByPaymentKey(String paymentKey) {
        return payments.values().stream()
                .filter(record -> record.getPaymentKey().equals(paymentKey))
                .findFirst();
    }

    // 승인이 남은 결제만 취소한다. 금액이 없으면 남은 잔액을 모두 취소한다. 결과와 그 뒤의 기록을 돌려준다
    public CancelAttempt cancel(String orderId, BigDecimal requestedAmount, String reason) {
        AtomicReference<CancelOutcome> outcome = new AtomicReference<>();
        PaymentRecord result = payments.computeIfPresent(orderId,
                (key, current) -> cancelIfPossible(current, requestedAmount, reason, outcome));
        return CancelAttempt.of(outcome.get(), result);
    }

    private PaymentRecord cancelIfPossible(PaymentRecord current, BigDecimal requestedAmount, String reason,
                                           AtomicReference<CancelOutcome> outcome) {
        CancelOutcome checked = check(current, requestedAmount);
        outcome.set(checked);
        if (checked.isCanceled()) {
            cancels.computeIfAbsent(current.getOrderId(), key -> new AtomicInteger()).incrementAndGet();
            return current.withCancel(CancelRecord.of(amountOf(current, requestedAmount), reason));
        }
        return current;
    }

    private static CancelOutcome check(PaymentRecord current, BigDecimal requestedAmount) {
        if (current.getState().isCanceled()) {
            return CancelOutcome.ALREADY_CANCELED;
        }
        if (current.getState().isNotCancelable()) {
            return CancelOutcome.NOT_CANCELABLE_PAYMENT;
        }
        BigDecimal amount = amountOf(current, requestedAmount);
        if (amount.signum() <= 0 || amount.compareTo(current.getBalanceAmount()) > 0) {
            return CancelOutcome.NOT_CANCELABLE_AMOUNT;
        }
        return CancelOutcome.CANCELED;
    }

    private static BigDecimal amountOf(PaymentRecord current, BigDecimal requestedAmount) {
        return Optional.ofNullable(requestedAmount).orElse(current.getBalanceAmount());
    }

    // 5장부터: 멱등키로 처음 보낸 응답. 키는 결제(paymentKey)마다 따로 본다 — 토스페이먼츠는 멱등키와 API 주소가 같아야 같은 요청으로 본다
    public Optional<ResponseEntity<Object>> idempotentResponse(String paymentKey, String idempotencyKey) {
        return Optional.ofNullable(idempotentResponses.get(paymentKey + " " + idempotencyKey));
    }

    // 같은 멱등키 요청을 처리하기 시작한다. 이미 처리 중이면 false
    public boolean startIdempotent(String paymentKey, String idempotencyKey) {
        return idempotentInFlight.add(paymentKey + " " + idempotencyKey);
    }

    public void finishIdempotent(String paymentKey, String idempotencyKey, ResponseEntity<Object> response) {
        idempotentResponses.putIfAbsent(paymentKey + " " + idempotencyKey, response);
        idempotentInFlight.remove(paymentKey + " " + idempotencyKey);
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

    // 결과와 상관없이 취소 요청을 받은 횟수(5장)
    public int cancelRequestCount(String orderId) {
        return Optional.ofNullable(cancelRequests.get(orderId)).map(AtomicInteger::get).orElse(0);
    }

    // 실제로 취소한 횟수(부분 취소 포함)
    public int cancelCount(String orderId) {
        return Optional.ofNullable(cancels.get(orderId)).map(AtomicInteger::get).orElse(0);
    }
}
