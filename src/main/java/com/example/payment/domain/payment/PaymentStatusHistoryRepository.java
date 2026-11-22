package com.example.payment.domain.payment;

import java.util.List;

/** 12장 결정 3: 결제 상태 이력. 남기기만 한다 */
public interface PaymentStatusHistoryRepository {

    void saveAll(List<PaymentStatusHistory> histories);
}
