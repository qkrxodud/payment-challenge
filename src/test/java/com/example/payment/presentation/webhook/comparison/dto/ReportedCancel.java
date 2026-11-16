package com.example.payment.presentation.webhook.comparison.dto;

import java.math.BigDecimal;

public record ReportedCancel(String transactionKey, BigDecimal cancelAmount, String cancelReason, String cancelStatus) {
}
