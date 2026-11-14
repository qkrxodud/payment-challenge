package com.example.payment.infrastructure.persistence.payment.storage;

/** READY와 APPROVED 사이에 AUTHENTICATED를 추가한 버전 */
public enum StatusV2 {
    READY, AUTHENTICATED, APPROVED, CAPTURED, CANCELED
}
