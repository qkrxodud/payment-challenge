package com.example.payment.presentation.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.PaymentConfirmService;
import com.example.payment.application.payment.PaymentPrepareService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** 6장: 실제 HTTP 로 부르는 웹훅 받기 API. 카드사 대신 시험이 알림 본문을 직접 보낸다 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("카드사 웹훅 받기 API")
class PaymentWebhookApiTest {

    private static final BigDecimal PREPARED = new BigDecimal("15000");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Test
    @DisplayName("승인된 결제의 승인 알림을 받으면 받았다고 답한다")
    void approvalNoticeIsAnsweredOk() {
        String orderId = approvedOrder();

        int status = post(notice("PAYMENT_STATUS_CHANGED", "{\"orderId\":\"" + orderId + "\",\"status\":\"DONE\"}"));

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("결제 상태 알림이 아닌 알림에도 받았다고 답한다")
    void otherEventIsAnsweredOk() {
        String orderId = approvedOrder();

        int status = post(notice("DEPOSIT_CALLBACK", "{\"orderId\":\"" + orderId + "\",\"status\":\"DONE\"}"));

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("주문번호가 없는 결제 상태 알림에도 받았다고 답한다")
    void noticeWithoutOrderIsAnsweredOk() {
        int status = post(notice("PAYMENT_STATUS_CHANGED", "{\"status\":\"DONE\"}"));

        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("읽을 수 없는 알림 본문에는 요청을 읽을 수 없다고 답한다")
    void unreadableNoticeIsRefused() {
        int status = post("{\"eventType\":");

        assertThat(status).isEqualTo(400);
    }

    private String approvedOrder() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        return orderId;
    }

    private static String notice(String eventType, String data) {
        return "{\"eventType\":\"" + eventType + "\",\"createdAt\":\"2026-11-16T10:00:00.000000\",\"data\":" + data + "}";
    }

    private int post(String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/webhooks/tosspayments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
