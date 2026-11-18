package com.example.payment.application.payment;

import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockRepository;
import com.example.payment.global.error.exception.CouponNotAvailableException;
import com.example.payment.global.error.exception.InsufficientPointException;
import com.example.payment.global.error.exception.OutOfStockException;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 8장: 결제가 잡는 재고·포인트·쿠폰. 결제를 바꾸는 기록(PaymentRecorder 등)이 같은 트랜잭션 안에서 부른다.
 * 결정 2: 카드사에 승인을 요청하기 직전, 인증을 남기는 트랜잭션 안에서 재고·쿠폰·포인트를 잡는다. 하나라도 모자라면 받지 않는다 —
 * 그 트랜잭션이 통째로 되돌려져 앞서 잡은 것과 인증도 남지 않고, 카드사에 승인을 요청하지 않는다(승인해 놓고 재고가 없는 결제를 만들지 않음).
 * 같은 순서(상품 번호 순 재고 → 쿠폰 → 포인트)로 잡아, 여러 상품을 담은 주문끼리 서로의 줄을 기다리며 멈추지 않게 한다.
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
                .forEach(item -> holdStock(payment, item));
        if (payment.usesCoupon()) {
            holdCoupon(payment);
        }
        if (payment.usesPoint()) {
            holdPoint(payment);
        }
    }

    private void holdStock(Payment payment, PaymentItem item) {
        if (stockRepository.decrease(item.getProductId(), item.getQuantity())) {
            return;
        }
        throw new OutOfStockException(payment.getOrderId());
    }

    private void holdCoupon(Payment payment) {
        if (couponRepository.use(payment.getCouponId(), payment.getId())) {
            return;
        }
        throw new CouponNotAvailableException(payment.getOrderId());
    }

    private void holdPoint(Payment payment) {
        if (pointRepository.use(payment.getMemberId(), payment.getPointAmount())) {
            return;
        }
        throw new InsufficientPointException(payment.getOrderId());
    }
}
