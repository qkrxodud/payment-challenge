package com.example.payment.domain.reconciliation;

import java.time.LocalDate;
import java.util.List;

/**
 * 12장 결정 1: 카드사 거래 내역. 그날(가맹점이 정한 시간대의 하루) 처리된 거래를 처리한 때 순으로 모두 돌려준다.
 * 시간 안에 답이 없으면 결제 승인과 같은 예외(PaymentGatewayNoResponseException)를, 서버 오류면 PaymentGatewayFailedException 을 던진다
 */
public interface GatewayTransactionSource {

    List<GatewayTransaction> findTransactions(LocalDate day);
}
