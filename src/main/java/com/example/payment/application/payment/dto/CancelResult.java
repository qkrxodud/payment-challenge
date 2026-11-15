package com.example.payment.application.payment.dto;

import com.example.payment.domain.payment.CancelStatus;
import java.math.BigDecimal;

// balanceAmount: 이 취소가 끝난 뒤 더 취소할 수 있는 금액. 결과를 모르면 비어 있다
public record CancelResult(String orderId, BigDecimal cancelAmount, CancelStatus cancelStatus, BigDecimal balanceAmount) {
}
