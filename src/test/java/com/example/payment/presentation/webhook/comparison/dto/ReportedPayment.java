package com.example.payment.presentation.webhook.comparison.dto;

import java.util.List;

public record ReportedPayment(String orderId, String status, List<ReportedCancel> cancels) {
}
