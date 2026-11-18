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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 8장: 상품을 담은 준비 요청으로 결제를 만든다. 담긴 상품을 찾아 그때의 상품가를 남기고, 쿠폰은 이 회원이 지금 쓸 수 있는지 본다.
 * 출발 구조: 결제 금액은 고객이 보낸 금액 그대로다.
 */
@Component
@RequiredArgsConstructor
public class PaymentOrders {

    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;

    public Payment order(PreparePaymentCommand command) {
        List<PaymentItem> items = itemsOf(command);
        checkCoupon(command);
        return Payment.order(command.orderId(), command.memberId(), items, command.couponId(), command.usePoint(),
                command.amount());
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

    private void checkCoupon(PreparePaymentCommand command) {
        if (Objects.isNull(command.couponId())) {
            return;
        }
        Coupon coupon = couponRepository.findById(command.couponId())
                .orElseThrow(() -> new CouponNotAvailableException(command.orderId()));
        if (coupon.isUsableBy(command.memberId())) {
            return;
        }
        throw new CouponNotAvailableException(command.orderId());
    }
}
