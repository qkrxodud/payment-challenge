package com.example.payment.presentation.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

/**
 * 승인 API 가 PG 답을 기다리는 동안 DB 연결을 붙잡는지, 실제 HTTP 요청으로 확인한다.
 * 웹 요청 단위로 연결을 붙잡는 설정(Open Session In View)까지 함께 걸리게 하려고 서비스가 아니라 API 를 부른다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.hikari.maximum-pool-size=2",
                "spring.datasource.hikari.connection-timeout=250"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("승인 대기 중 조회 — 운영 설정")
class PendingApprovalApiTest {

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    MockPgServer mockPg;

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
    }

    @Test
    @DisplayName("카드사 답을 기다리는 승인 요청이 연결 수만큼 있어도 다른 결제를 조회할 수 있다")
    void lookupWhileApprovalsWait() {
        PaymentApi api = new PaymentApi(jsonMapper, port);
        String other = newOrderId();
        String first = newOrderId();
        String second = newOrderId();
        api.prepare(other, 15000);
        api.prepare(first, 15000);
        api.prepare(second, 15000);
        mockPg.respondAfter(Duration.ofMillis(800));

        CompletableFuture<?> firstApproval = CompletableFuture.runAsync(() -> api.confirm(first));
        CompletableFuture<?> secondApproval = CompletableFuture.runAsync(() -> api.confirm(second));
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(first) && mockPg.isWaiting(second));

        int lookupStatus = api.find(other).status();
        // 다음 테스트가 남은 승인 요청의 영향을 받지 않도록 결과를 보기 전에 모두 끝낸다
        CompletableFuture.allOf(firstApproval, secondApproval).join();

        assertThat(lookupStatus).isEqualTo(200);
    }

    private static String newOrderId() {
        return "order-" + UUID.randomUUID();
    }
}
