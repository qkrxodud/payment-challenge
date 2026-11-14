package com.example.payment.infrastructure.persistence.payment.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상태를 하나 추가해 배포한 뒤 애플리케이션이 보는 모양. 같은 테이블을 읽는다. */
@Entity
@Table(name = "status_storage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredPaymentV2 {

    @Id
    private Long id;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "ordinal_status")
    private StatusV2 ordinalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "string_status")
    private StatusV2 stringStatus;
}
