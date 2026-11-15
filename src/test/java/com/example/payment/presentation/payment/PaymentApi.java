package com.example.payment.presentation.payment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 실제 HTTP 로 결제 API 를 부르는 테스트 도우미 */
class PaymentApi {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper;
    private final String baseUrl;

    PaymentApi(JsonMapper jsonMapper, int port) {
        this.jsonMapper = jsonMapper;
        this.baseUrl = "http://localhost:" + port + "/api/v1/payments";
    }

    Answer prepare(String orderId, long amount) {
        return post("", "{\"orderId\":\"" + orderId + "\",\"amount\":" + amount + "}");
    }

    Answer confirm(String orderId, long amount) {
        return confirm(orderId, "key-" + orderId, String.valueOf(amount));
    }

    // 금액은 JSON 에 적힌 그대로 보낸다(빈 값, 소수 자릿수 등을 시험하려고 글자로 받는다)
    Answer confirm(String orderId, String paymentKey, String amount) {
        return post("/" + orderId + "/confirm",
                "{\"paymentKey\":\"" + paymentKey + "\",\"amount\":" + amount + "}");
    }

    Answer find(String orderId) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + "/" + orderId)).GET());
    }

    Answer delete(String orderId) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + "/" + orderId)).DELETE());
    }

    Answer post(String path, String json) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private Answer send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Answer(response.statusCode(), jsonMapper.readTree(response.body()));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    /** 응답 상태 코드와 본문 */
    static class Answer {

        private final int status;
        private final JsonNode body;

        Answer(int status, JsonNode body) {
            this.status = status;
            this.body = body;
        }

        int status() {
            return status;
        }

        String field(String name) {
            return body.path(name).asString();
        }
    }
}
