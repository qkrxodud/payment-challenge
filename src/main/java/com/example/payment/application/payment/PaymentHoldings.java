package com.example.payment.application.payment;

import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.error.exception.CouponNotAvailableException;
import com.example.payment.domain.error.exception.InsufficientPointException;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockReservations;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 8장: 결제가 잡는 재고·포인트·쿠폰. 결제를 바꾸는 기록(PaymentRecorder 등)이 같은 트랜잭션 안에서 부른다.
 * 결정 2: 카드사에 승인을 요청하기 직전, 인증을 남기는 트랜잭션 안에서 쿠폰·포인트를 잡는다. 하나라도 모자라면 받지 않는다 —
 * 그 트랜잭션이 통째로 되돌려져 앞서 잡은 것과 인증도 남지 않고, 카드사에 승인을 요청하지 않는다(승인해 놓고 잡지 못한 결제를 만들지 않음).
 * 결정 4: 잡아 둔 것은 결제 상태가 바뀌는 그 트랜잭션에서 한 번 돌려준다(승인 실패·만료·전액 취소). 결제를 바꾸는 기록은 모두 7장 결제 줄 잠금
 * 안에서 하므로, 같은 실패를 거절 답 기록·보정·알림 처리가 겹쳐 닫아도 상태를 바꾼 먼저 기록만 돌려준다. 결과 모름은 쥔 채로 둔다.
 * 10장 결정 1: 재고는 재고 서비스에 있어 인증 트랜잭션에 넣지 않는다. 인증(쿠폰·포인트)을 남긴 뒤, 카드사에 승인을 요청하기 전에
 * 재고 서비스에 동기 HTTP 로 잡는다(reserveStock) — 8장 규칙(승인해 놓고 재고가 없는 결제를 만들지 않음)과 지금 API 를 그대로 지킨다.
 * 비용: 재고 서비스가 멈추거나 느리면 결제도 받지 못하거나 그만큼 느려진다.
 * 10장 결정 3: 재고 서비스는 결제 번호로 예약을 남긴다. 결제가 승인되면 확정하고, 승인 없이 끝나거나 전액 취소되면 해제한다(settle).
 * 10장 결정 2: 이 Saga(재고 예약 → 카드사 승인 → 확정/해제)의 진행은 결제 서버가 쥔다(오케스트레이션). 다음 단계는 결제 상태가 정하고,
 * 예약·확정·해제는 결제 서버가 정해 재고 서비스에 보낸다 — 재고 서비스는 결제 상태가 무엇인지 몰라도 된다(받은 명령대로 예약 상태만 바꾼다).
 * 결제 상태가 바뀌는 곳은 결제를 바꾸는 기록 다섯 곳뿐이고 모두 settle 을 부르므로, 어느 상태 전이가 어느 재고 단계인지는 여기 한 곳에 있다.
 * 10장 결정 5: 확정·해제는 그 자리에서 HTTP 로 보내지 않고 같은 트랜잭션에서 payment_outbox 에 남긴다(StockCommands). 릴레이가 보낸다.
 * 결과 모름 결제는 4장 보정이 결과를 정할 때까지 아무 명령도 남기지 않는다 — 예약을 쥔 채로 둔다.
 */
@Component
@RequiredArgsConstructor
public class PaymentHoldings {

    private final StockReservations stockReservations;
    private final StockCommands stockCommands;
    private final CouponRepository couponRepository;
    private final PointRepository pointRepository;
    private final Clock clock;

    public void hold(Payment payment) {
        if (payment.usesCoupon()) {
            holdCoupon(payment);
        }
        if (payment.usesPoint()) {
            holdPoint(payment);
        }
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

    // 10장 결정 1: 담긴 상품의 재고를 재고 서비스에 잡는다(결정 3부터 결제 번호를 예약 번호로). 상품이 없는 결제(금액만 받은 결제)는 잡을 것이 없다
    public boolean reserveStock(Long paymentId, String orderId, List<PaymentItem> items) {
        if (items.isEmpty()) {
            return true;
        }
        return stockReservations.reserve(paymentId, orderId, items);
    }

    // 10장 결정 3: 결제를 바꾸는 기록이 상태를 바꾼 바로 뒤에 부른다. 승인으로 닫았으면 재고 예약을 확정하고,
    // 승인 없이 끝냈거나 승인을 모두 취소했으면 잡아 둔 것을 돌려준다(재고 예약은 해제)
    public void settle(Payment payment, PaymentStatus before) {
        confirmIfApproved(payment, before);
        releaseIfClosed(payment, before);
    }

    private void confirmIfApproved(Payment payment, PaymentStatus before) {
        if (payment.confirmsHoldingsFrom(before) && payment.hasItems()) {
            stockCommands.confirm(payment);
        }
    }

    // 이 기록이 결제를 before 에서 지금 상태로 바꾸며 승인 없이 끝냈거나 승인을 모두 취소했으면 잡아 둔 것을 돌려준다
    public void releaseIfClosed(Payment payment, PaymentStatus before) {
        if (payment.releasesHoldingsFrom(before)) {
            release(payment);
        }
    }

    // 10장 결정 1: 재고를 잡지 못해 승인 없이 닫은 결제. 인증 때 잡은 쿠폰·포인트만 돌려준다
    public void releaseUnreservedIfClosed(Payment payment, PaymentStatus before) {
        if (payment.releasesHoldingsFrom(before)) {
            releaseCouponAndPoint(payment);
        }
    }

    public void release(Payment payment) {
        if (payment.hasItems()) {
            stockCommands.release(payment);
        }
        releaseCouponAndPoint(payment);
    }

    // 인증 때 잡은 쿠폰·포인트를 돌려준다
    public void releaseCouponAndPoint(Payment payment) {
        if (payment.usesCoupon()) {
            couponRepository.restore(payment.getCouponId(), payment.getId());
        }
        if (payment.usesPoint()) {
            pointRepository.restore(payment.getMemberId(), payment.getId(), payment.getPointAmount(), clock.instant());
        }
    }
}
