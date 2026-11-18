package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentHoldings;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockRepository;
import org.springframework.context.annotation.Primary;

/**
 * 비교 구현(8장 결정 2)이 쓰는 잡아 두기: 인증 때는 잡지 않고, 비교 구현이 정한 때(준비 뒤, 승인 뒤)에 holdNow 로 잡는다.
 * 잡는 방법은 운영과 같다.
 */
@Primary
public class DeferredHoldings extends PaymentHoldings {

    public DeferredHoldings(StockRepository stockRepository, CouponRepository couponRepository,
                            PointRepository pointRepository) {
        super(stockRepository, couponRepository, pointRepository);
    }

    @Override
    public void hold(Payment payment) {
        // 인증 때는 잡지 않는다
    }

    public void holdNow(Payment payment) {
        super.hold(payment);
    }
}
