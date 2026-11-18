package com.example.payment.domain.point;

import java.math.BigDecimal;

/** 8장: 회원의 포인트 잔액 */
public interface PointRepository {

    // 회원의 포인트를 이만큼 쓴다
    void use(String memberId, BigDecimal amount);
}
