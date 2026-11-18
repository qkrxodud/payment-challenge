package com.example.payment.domain.point;

import java.math.BigDecimal;
import java.time.Instant;

/** 8장: 회원의 포인트 잔액. 8장 결정 4부터 쓰고 되돌릴 때마다 증감 이력을 결제마다 한 번씩 남긴다 */
public interface PointRepository {

    // 잔액이 이만큼 있으면 이 결제에 쓰고 맞다고 답한다. 모자라거나 포인트가 없는 회원이면 쓰지 않고 아니라고 답한다
    boolean use(String memberId, Long paymentId, BigDecimal amount, Instant now);

    // 이 결제에 쓴 포인트를 돌려준다. 이 결제에 이미 돌려줬으면 다시 돌려주지 않는다
    void restore(String memberId, Long paymentId, BigDecimal amount, Instant now);
}
