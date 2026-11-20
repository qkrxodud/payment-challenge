package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.comparison.dto.QuantityStockRequest;
import com.example.payment.domain.error.exception.InventoryNoResponseException;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.stock.StockReservations;
import com.example.payment.global.config.InventoryProperties;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 비교 구현(10장 결정 3, 수량만): 예약 기록 없이 재고 서비스에 수량만 바로 줄인다(잡기). 해제는 QuantityStockCommandSender 가 수량을 실어 보낸다.
 * 어느 결제의 요청인지 보내지 않으므로, 해제가 줄이기보다 먼저 도착하면 먼저 늘리고 늦게 온 줄이기를 그대로 반영하고,
 * 같은 해제가 두 번 오면 두 번 늘린다. 재고 서비스에는 QuantityStockController·QuantityStockRecorder·QuantityStockCommandListener 를 끼운다.
 */
@Primary
public class QuantityStockReservations implements StockReservations {

    private final RestClient inventoryRestClient;

    public QuantityStockReservations(InventoryProperties inventoryProperties) {
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
    public boolean reserve(Long reservationId, String orderId, List<PaymentItem> items) {
        try {
            return inventoryRestClient.post()
                    .uri("/stocks/decrease")
                    .body(requestOf(items))
                    .exchange((request, response) -> response.getStatusCode().is2xxSuccessful());
        } catch (ResourceAccessException noResponse) {
            throw new InventoryNoResponseException(orderId);
        }
    }

    static QuantityStockRequest requestOf(List<PaymentItem> items) {
        return new QuantityStockRequest(items.stream()
                .map(item -> new QuantityStockRequest.Line(item.getProductId(), item.getQuantity()))
                .toList());
    }
}
