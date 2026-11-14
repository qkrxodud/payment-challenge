package com.example.payment.infrastructure.persistence.payment.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

/** 상태를 추가하기 전 애플리케이션이 보는 모양 */
@Entity
@Table(name = "status_storage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredPaymentV1 {

    @Id
    private Long id;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "ordinal_status")
    private StatusV1 ordinalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "string_status")
    private StatusV1 stringStatus;

    @Builder
    private StoredPaymentV1(Long id, StatusV1 ordinalStatus, StatusV1 stringStatus) {
        this.id = id;
        this.ordinalStatus = ordinalStatus;
        this.stringStatus = stringStatus;
    }

    public static StoredPaymentV1 of(Long id, StatusV1 status) {
        return StoredPaymentV1.builder()
                .id(id)
                .ordinalStatus(status)
                .stringStatus(status)
                .build();
    }
}
