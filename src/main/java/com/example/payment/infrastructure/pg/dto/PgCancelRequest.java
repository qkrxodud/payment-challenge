package com.example.payment.infrastructure.pg.dto;

import java.math.BigDecimal;

public record PgCancelRequest(String cancelReason, BigDecimal cancelAmount) {
}
