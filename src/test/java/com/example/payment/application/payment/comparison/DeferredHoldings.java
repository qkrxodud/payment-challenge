package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentHoldings;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.error.exception.OutOfStockException;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockReservations;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Primary;

/**
 * 비교 구현(8장 결정 2)이 쓰는 잡아 두기: 인증 때는 잡지 않고, 비교 구현이 정한 때(준비 뒤, 승인 뒤)에 holdNow 로 잡는다.
 * 잡는 방법은 운영과 같다.
 */
@Primary
public class DeferredHoldings extends PaymentHoldings {

    public DeferredHoldings(StockReservations stockReservations, CouponRepository couponRepository,
                            PointRepository pointRepository, Clock clock) {
        super(stockReservations, couponRepository, pointRepository, clock);
    }

    @Override
    public void hold(Payment payment) {
        // 인증 때는 잡지 않는다
    }

    // 10장 결정 1부터 재고는 인증 뒤 재고 서비스에 따로 잡는다. 이 비교는 정한 때에 쿠폰·포인트와 함께 재고도 잡는다(모자라면 받지 않음)
    public void holdNow(Payment payment) {
        super.hold(payment);
        if (super.reserveStock(payment.getId(), payment.getOrderId(), payment.getItems())) {
            return;
        }
        throw new OutOfStockException(payment.getOrderId());
    }

    // 10장 결정 1부터: 승인을 요청하기 전에 잡지 않는다(정한 때에 holdNow 로 잡는다)
    @Override
    public boolean reserveStock(Long paymentId, String orderId, List<PaymentItem> items) {
        return true;
    }

    // 8장 결정 4부터: 이 비교는 잡는 때만 본다. 잡지 못한 채 승인을 취소한 결제도 있어(승인 뒤 잡기) 상태로 되돌리지 않는다
    @Override
    public void releaseIfClosed(Payment payment, PaymentStatus before) {
        // 되돌리지 않는다
    }
}
