package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.OrderLine;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.coupon.Coupon;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.product.Product;
import com.example.payment.domain.product.ProductRepository;
import com.example.payment.global.error.exception.CouponNotAvailableException;
import com.example.payment.global.error.exception.InvalidQuantityException;
import com.example.payment.global.error.exception.ProductNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 8장: 상품을 담은 준비 요청으로 결제를 만든다. 담긴 상품을 찾아 그때의 상품가를 남기고, 쿠폰은 이 회원이 지금 쓸 수 있는지 본다.
 * 결정 1: 결제 금액은 서버가 계산한다(상품가 합 − 쿠폰 할인 − 쓴 포인트). 고객이 보낸 금액이 그와 다르면 3장 승인 때와 같은 이유
 * (금액이 맞지 않음, 400 AMOUNT_MISMATCH)로 받지 않는다 — 고객이 할인을 부풀리거나 금액을 낮춰 보내도 그 금액으로 결제가 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentOrders {

    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;

    public Payment order(PreparePaymentCommand command) {
        List<PaymentItem> items = itemsOf(command);
        Payment payment = Payment.order(command.orderId(), command.memberId(), items, command.couponId(),
                discountOf(command), command.usePoint());
        payment.verifyAmount(command.amount());
        return payment;
    }

    private List<PaymentItem> itemsOf(PreparePaymentCommand command) {
        Map<Long, Product> products = productRepository.findAllById(command.items().stream()
                        .map(OrderLine::productId)
                        .toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return command.items().stream()
                .map(line -> itemOf(command.orderId(), line, products.get(line.productId())))
                .toList();
    }

    private static PaymentItem itemOf(String orderId, OrderLine line, Product product) {
        if (Objects.isNull(product)) {
            throw new ProductNotFoundException(orderId);
        }
        if (Objects.isNull(line.quantity()) || line.quantity() <= 0) {
            throw new InvalidQuantityException(orderId);
        }
        return PaymentItem.of(product.getId(), line.quantity(), product.getPrice());
    }

    // 쿠폰 할인 금액. 쿠폰이 없으면 0 원이다
    private BigDecimal discountOf(PreparePaymentCommand command) {
        if (Objects.isNull(command.couponId())) {
            return BigDecimal.ZERO;
        }
        Coupon coupon = couponRepository.findById(command.couponId())
                .orElseThrow(() -> new CouponNotAvailableException(command.orderId()));
        if (coupon.isUsableBy(command.memberId())) {
            return coupon.getDiscountAmount();
        }
        throw new CouponNotAvailableException(command.orderId());
    }
}
