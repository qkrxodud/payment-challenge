package com.example.payment.application.payment;

import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockRepository;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 8장: 결제가 잡는 재고·포인트·쿠폰. 결제를 바꾸는 기록(PaymentRecorder 등)이 같은 트랜잭션 안에서 부른다.
 * 출발 구조: 승인된 결제의 재고·쿠폰·포인트를 줄인다.
 */
@Component
@RequiredArgsConstructor
public class PaymentHoldings {

    private final StockRepository stockRepository;
    private final CouponRepository couponRepository;
    private final PointRepository pointRepository;

    public void hold(Payment payment) {
        payment.getItems().stream()
                .sorted(Comparator.comparing(PaymentItem::getProductId))
                .forEach(item -> stockRepository.decrease(item.getProductId(), item.getQuantity()));
        if (payment.usesCoupon()) {
            couponRepository.use(payment.getCouponId(), payment.getId());
        }
        if (payment.usesPoint()) {
            pointRepository.use(payment.getMemberId(), payment.getPointAmount());
        }
    }
}
