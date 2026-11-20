package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentHoldings;
import com.example.payment.application.payment.comparison.dto.ReservationView;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.error.exception.InventoryNoResponseException;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.point.PointRepository;
import com.example.payment.domain.stock.StockReservations;
import com.example.payment.global.config.InventoryProperties;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 비교 구현(10장 결정 4, 조회로 확인): 재고 서비스의 답을 받지 못하면 그 예약을 재고 서비스에 조회해 확인한 뒤 진행한다 —
 * 잡혀 있으면 카드사에 승인을 요청하고, 없거나 해제됐으면 재고가 모자란 것과 같이 닫는다(해제는 보내지 않는다). 조회도 답이 없으면 응답 없음으로 닫는다.
 * 조회한 뒤에 예약 요청이 늦게 도착하면 아무도 그 예약을 해제하지 않는다.
 */
@Primary
public class InquiringHoldings extends PaymentHoldings {

    private static final Set<String> HELD = Set.of("RESERVED", "CONFIRMED");

    private final RestClient inventoryRestClient;

    public InquiringHoldings(StockReservations stockReservations, CouponRepository couponRepository,
                             PointRepository pointRepository, Clock clock, InventoryProperties inventoryProperties) {
        super(stockReservations, couponRepository, pointRepository, clock);
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
    public boolean reserveStock(Long paymentId, String orderId, List<PaymentItem> items) {
        try {
            return super.reserveStock(paymentId, orderId, items);
        } catch (InventoryNoResponseException noResponse) {
            return isHeld(paymentId, orderId);
        }
    }

    private boolean isHeld(Long paymentId, String orderId) {
        try {
            return inventoryRestClient.get()
                    .uri("/reservations/{reservationId}", paymentId)
                    .exchange((request, response) -> response.getStatusCode().is2xxSuccessful()
                            && HELD.contains(response.bodyTo(ReservationView.class).status()));
        } catch (ResourceAccessException noResponse) {
            throw new InventoryNoResponseException(orderId);
        }
    }
}
