package com.example.payment.domain.point;

import java.math.BigDecimal;

/** 8장: 회원의 포인트 잔액 */
public interface PointRepository {

    // 잔액이 이만큼 있으면 쓰고 맞다고 답한다. 모자라거나 포인트가 없는 회원이면 쓰지 않고 아니라고 답한다
    boolean use(String memberId, BigDecimal amount);
}
