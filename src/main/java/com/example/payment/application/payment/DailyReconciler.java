package com.example.payment.application.payment;

import com.example.payment.domain.error.exception.PaymentGatewayFailedException;
import com.example.payment.domain.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.domain.reconciliation.GatewayPaymentDay;
import com.example.payment.domain.reconciliation.GatewayTransactionSource;
import com.example.payment.domain.reconciliation.Mismatch;
import com.example.payment.domain.reconciliation.OurPayment;
import com.example.payment.domain.reconciliation.ReconciliationRepository;
import com.example.payment.global.config.DailyReconciliationProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 1: 하루치 대사. 그날 카드사 거래 내역을 모두 읽어 결제 키마다 묶고, 그날 인증한 우리 결제(와 그날 카드사 거래가 있는 우리 결제)를
 * 결제 키로 맞춰 본다. 상태가 다르거나 금액이 다르거나 한쪽에만 있는 결제를 대사 결과 표에 남긴다.
 * 4장 보정과 6장 따라가기는 결과 모름·인증 상태와 받은 알림만 본다 — 알림이 모두 닿지 않은 카드사 쪽 변화와, 한쪽에만 있는 결제는 이 대사로만 찾는다.
 * 결제마다 카드사에 묻지 않고 거래 내역을 쪽 단위로 읽는다 — 결제 수만큼 조회하지 않고, 카드사에만 있는 결제도 거래 내역에서 찾는다.
 * 12장 결정 2부터 찾은 어긋남은 MismatchResolver 가 처리한다(우리 기록이 뒤처진 결제만 6장 따라가기로 맞추고 나머지는 운영자 확인).
 * 서버 여러 대 가운데 작업 잠금(7장 job_lock)을 맡은 한 대만 돈다. 같은 날을 다시 돌려도 같은 어긋남은 한 줄만 남는다(대사 결과 표의 고유 키).
 * 카드사가 답하지 않으면 아무것도 남기지 않고 끝낸다 — 다음에 같은 날을 다시 돌린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReconciler {

    private final ExclusiveJobRunner exclusiveJobRunner;
    private final GatewayTransactionSource gatewayTransactionSource;
    private final ReconciliationRepository reconciliationRepository;
    private final MismatchResolver mismatchResolver;
    private final DailyReconciliationProperties dailyReconciliationProperties;
    private final Clock clock;

    public void reconcile(LocalDate day) {
        exclusiveJobRunner.run(ExclusiveJobRunner.DAILY_RECONCILIATION, () -> reconcileLocked(day));
    }

    private void reconcileLocked(LocalDate day) {
        try {
            reconciliationRepository.record(day, mismatchesOf(day), clock.instant());
        } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException unanswered) {
            log.warn("Could not read the gateway transactions for {} ({}). The day will be reconciled again next time.",
                    day, unanswered.getClass().getSimpleName());
        }
    }

    private List<Mismatch> mismatchesOf(LocalDate day) {
        Map<String, GatewayPaymentDay> gateway = GatewayPaymentDay.byPaymentKey(gatewayTransactionSource.findTransactions(day));
        Map<String, OurPayment> ours = oursOf(day, gateway.keySet());
        Set<String> paymentKeys = new LinkedHashSet<>(ours.keySet());
        paymentKeys.addAll(gateway.keySet());
        return paymentKeys.stream()
                .map(paymentKey -> Mismatch.between(ours.get(paymentKey), gateway.get(paymentKey)))
                .flatMap(Optional::stream)
                .map(mismatchResolver::resolve)
                .toList();
    }

    // 그날 인증한 결제와, 그날 카드사 거래가 있는데 앞날에 인증한 결제(앞날 승인의 오늘 취소 등)
    private Map<String, OurPayment> oursOf(LocalDate day, Set<String> gatewayKeys) {
        Instant from = day.atStartOfDay(dailyReconciliationProperties.getZone()).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(dailyReconciliationProperties.getZone()).toInstant();
        Map<String, OurPayment> ours = new LinkedHashMap<>(byKey(reconciliationRepository.findAuthenticatedBetween(from, to)));
        Set<String> missing = gatewayKeys.stream()
                .filter(Predicate.not(ours::containsKey))
                .collect(Collectors.toSet());
        ours.putAll(byKey(reconciliationRepository.findByPaymentKeys(missing)));
        return ours;
    }

    private static Map<String, OurPayment> byKey(List<OurPayment> payments) {
        return payments.stream().collect(Collectors.toMap(OurPayment::getPaymentKey, Function.identity(), (first, second) -> first,
                LinkedHashMap::new));
    }
}
