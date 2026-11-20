package com.example.payment.application.payment;

import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.error.exception.CouponNotAvailableException;
import com.example.payment.domain.error.exception.InsufficientPointException;
import com.example.payment.domain.error.exception.OutOfStockException;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockRepository;
import java.time.Clock;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 8장: 결제가 잡는 재고·포인트·쿠폰. 결제를 바꾸는 기록(PaymentRecorder 등)이 같은 트랜잭션 안에서 부른다.
 * 결정 2: 카드사에 승인을 요청하기 직전, 인증을 남기는 트랜잭션 안에서 재고·쿠폰·포인트를 잡는다. 하나라도 모자라면 받지 않는다 —
 * 그 트랜잭션이 통째로 되돌려져 앞서 잡은 것과 인증도 남지 않고, 카드사에 승인을 요청하지 않는다(승인해 놓고 재고가 없는 결제를 만들지 않음).
 * 같은 순서(상품 번호 순 재고 → 쿠폰 → 포인트)로 잡아, 여러 상품을 담은 주문끼리 서로의 줄을 기다리며 멈추지 않게 한다.
 * 결정 4: 잡아 둔 것은 결제 상태가 바뀌는 그 트랜잭션에서 한 번 돌려준다(승인 실패·만료·전액 취소). 결제를 바꾸는 기록은 모두 7장 결제 줄 잠금
 * 안에서 하므로, 같은 실패를 거절 답 기록·보정·알림 처리가 겹쳐 닫아도 상태를 바꾼 먼저 기록만 돌려준다. 결과 모름은 쥔 채로 둔다.
 * 10장 출발 구조: 재고는 재고 서비스로 옮겼다. 재고 저장소가 HTTP 호출(InventoryStockClient)로 바뀌었을 뿐 잡고 돌려주는 자리는 그대로다.
 */
@Component
@RequiredArgsConstructor
public class PaymentHoldings {

    private final StockRepository stockRepository;
    private final CouponRepository couponRepository;
    private final PointRepository pointRepository;
    private final Clock clock;

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
        if (pointRepository.use(payment.getMemberId(), payment.getId(), payment.getPointAmount(), clock.instant())) {
            return;
        }
        throw new InsufficientPointException(payment.getOrderId());
    }

    // 이 기록이 결제를 before 에서 지금 상태로 바꾸며 승인 없이 끝냈거나 승인을 모두 취소했으면 잡아 둔 것을 돌려준다
    public void releaseIfClosed(Payment payment, PaymentStatus before) {
        if (payment.releasesHoldingsFrom(before)) {
            release(payment);
        }
    }

    // 잡을 때와 같은 순서로 돌려준다
    public void release(Payment payment) {
        payment.getItems().stream()
                .sorted(Comparator.comparing(PaymentItem::getProductId))
                .forEach(item -> stockRepository.increase(item.getProductId(), item.getQuantity()));
        if (payment.usesCoupon()) {
            couponRepository.restore(payment.getCouponId(), payment.getId());
        }
        if (payment.usesPoint()) {
            pointRepository.restore(payment.getMemberId(), payment.getId(), payment.getPointAmount(), clock.instant());
        }
    }
}
