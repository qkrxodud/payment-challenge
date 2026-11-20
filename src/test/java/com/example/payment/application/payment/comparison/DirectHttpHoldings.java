package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentHoldings;
import com.example.payment.application.payment.StockCommands;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockReservations;
import com.example.payment.global.config.InventoryProperties;
import java.net.http.HttpClient;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 비교 구현(10장 결정 5, 그 자리에서 HTTP — 결정 3~4 의 운영): 결제 상태를 바꾼 그 트랜잭션 안에서 재고 서비스에 바로 HTTP 로 확정·해제를 보낸다.
 * 실패하면 결제 기록을 막지 않게 로그만 남긴다 — 재고 서비스가 멈춘 동안 보낸 해제는 사라진다. 결제 줄을 잠근 채 재고 서비스의 답을 기다린다.
 * 쿠폰·포인트는 운영과 같이 돌려준다.
 */
@Slf4j
@Primary
public class DirectHttpHoldings extends PaymentHoldings {

    private final RestClient inventoryRestClient;

    public DirectHttpHoldings(StockReservations stockReservations, StockCommands stockCommands,
                              CouponRepository couponRepository, PointRepository pointRepository, Clock clock,
                              InventoryProperties inventoryProperties) {
        super(stockReservations, stockCommands, couponRepository, pointRepository, clock);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(inventoryProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(inventoryProperties.getReadTimeout());
        this.inventoryRestClient = RestClient.builder()
                .baseUrl(inventoryProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void settle(Payment payment, PaymentStatus before) {
        if (payment.hasItems() && payment.confirmsHoldingsFrom(before)) {
            send("/reservations/{reservationId}/confirm", payment);
        }
        if (payment.hasItems() && payment.releasesHoldingsFrom(before)) {
            send("/reservations/{reservationId}/release", payment);
        }
        releaseUnreservedIfClosed(payment, before);
    }

    private void send(String uri, Payment payment) {
        try {
            inventoryRestClient.post()
                    .uri(uri, payment.getId())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException notSent) {
            log.warn("Could not send {} for order {}: {}.", uri, payment.getOrderId(), notSent.getClass().getSimpleName());
        }
    }
}
