package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentHoldings;
import com.example.payment.application.payment.StockCommands;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockReservations;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Primary;

/**
 * 비교 구현(10장 결정 1, 승인 먼저): 승인을 요청하기 전에 재고를 잡지 않는다 — 승인된 뒤 재고 서비스에 Kafka 로 차감을 보낸다.
 * 돌려줄 때 재고는 재고 서비스가 줄였다고 답한 결제만 돌려준다(줄이지 못해 카드사 취소로 보상한 결제는 쿠폰·포인트만).
 * 재고 서비스는 예약 기록 없이 수량만 바꾼다(QuantityStockReservations 를 함께 끼운다 — 돌려주기는 수량 늘리기). 확정은 없다.
 */
@Primary
public class ApproveFirstHoldings extends PaymentHoldings {

    public ApproveFirstHoldings(StockReservations stockReservations, StockCommands stockCommands, CouponRepository couponRepository,
                                PointRepository pointRepository, Clock clock) {
        super(stockReservations, stockCommands, couponRepository, pointRepository, clock);
    }

    @Override
    public boolean reserveStock(Long paymentId, String orderId, List<PaymentItem> items) {
        return true;
    }

    @Override
    public void release(Payment payment) {
        if (StockReplies.isDecreased(payment.getOrderId())) {
            super.release(payment);
            return;
        }
        releaseCouponAndPoint(payment);
    }
}
