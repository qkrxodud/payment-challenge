package com.example.payment.domain.reconciliation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 12장 결정 1: 대사가 읽는 우리 결제와 대사 결과 표(reconciliation_result).
 */
public interface ReconciliationRepository {

    // 결제 키가 있고(인증까지 간 결제) 인증한 때가 [from, to) 안인 결제
    List<OurPayment> findAuthenticatedBetween(Instant from, Instant to);

    // 이 결제 키들의 결제(인증한 날과 상관없이). 없는 키는 빠진다
    List<OurPayment> findByPaymentKeys(Collection<String> paymentKeys);

    // 그날의 어긋남을 남긴다. 같은 날·같은 결제 키·같은 종류는 한 줄만 남기고, 다시 찾으면 그 줄의 값과 처리를 바꾼다
    void record(LocalDate day, List<Mismatch> mismatches, Instant foundAt);

    // 12장 결정 5: 운영자가 확인할 어긋남의 수(날짜와 상관없이)
    long countNeedingReview();
}
