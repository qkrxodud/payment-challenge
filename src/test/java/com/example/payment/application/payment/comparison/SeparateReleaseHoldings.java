package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentHoldings;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockReservations;
import java.time.Clock;
import org.springframework.context.annotation.Primary;

/**
 * 비교 구현(8장 결정 4): 실패를 기록하는 곳마다 따로 되돌리기. 거절 답 기록·보정·알림 처리·취소 기록이 저마다 결제를 보고
 * 승인 없이 끝났거나 취소된 결제면 잡아 둔 것을 돌려준다 — 이 기록이 그 상태로 바꿨는지는 보지 않는다.
 */
@Primary
public class SeparateReleaseHoldings extends PaymentHoldings {

    public SeparateReleaseHoldings(StockReservations stockReservations, CouponRepository couponRepository,
                                   PointRepository pointRepository, Clock clock) {
        super(stockReservations, couponRepository, pointRepository, clock);
    }

    @Override
    public void releaseIfClosed(Payment payment, PaymentStatus before) {
        if (payment.getStatus().dropsHoldings()) {
            release(payment);
        }
    }
}
