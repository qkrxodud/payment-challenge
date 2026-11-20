package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentHoldings;
import com.example.payment.application.payment.StockCommands;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockReservations;
import java.time.Clock;
import org.springframework.context.annotation.Primary;

/**
 * 비교 구현(10장 결정 2, 코레오그래피): 결제 서버는 재고 예약만 하고(결정 1·3 그대로) 확정·해제를 정해 보내지 않는다.
 * 재고 서비스가 결제 이벤트(9장 결제 결과)를 읽고 스스로 확정·해제한다(PaymentEventReactionListener). 쿠폰·포인트는 결제 서버가 그대로 돌려준다.
 */
@Primary
public class ChoreographyHoldings extends PaymentHoldings {

    public ChoreographyHoldings(StockReservations stockReservations, StockCommands stockCommands, CouponRepository couponRepository,
                                PointRepository pointRepository, Clock clock) {
        super(stockReservations, stockCommands, couponRepository, pointRepository, clock);
    }

    @Override
    public void settle(Payment payment, PaymentStatus before) {
        releaseUnreservedIfClosed(payment, before);
    }
}
