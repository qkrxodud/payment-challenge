package com.example.payment.application.payment;

import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.OrderServer;
import com.example.payment.OrderServerConfiguration;
import com.example.payment.OtherPaymentServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.comparison.SkipLockedOutboxRelay;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 9장 결정 3 측정(./gradlew outboxMeasurement): 결제 100건을 승인 뒤 전액 취소해 보낼 결과 200건을 쌓아 두고(릴레이는 꺼 둠),
 * 서버 두 대가 같은 때 릴레이를 쉬지 않고 돌려(한 주기가 끝나면 바로 다음 주기) 보낼 것이 없어질 때까지의 시간, 처리량(200 / 시간),
 * 주문 서비스가 받은 이벤트의 중복·순서 위반·주문 상태 어긋남을 릴레이를 나누는 방식마다 센다. 브로커는 끊지 않는다. 방식마다 3번.
 * 주기 하나는 50건까지 읽는다(운영 batch-size). 한 줄씩 나눠 보내기는 한 줄씩 맡는다.
 */
@Tag("outbox-measurement")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, OrderServerConfiguration.class, SkipLockedOutboxRelay.class})
class RelayThroughputMeasurement {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final int PAYMENTS = 100;
    static final int ROUNDS = 3;

    private static ConfigurableApplicationContext otherServer;

    @Autowired
    ApplicationContext thisServer;

    @Autowired
    MySQLContainer mysql;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    OrderServer orderServer;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentCancelService paymentCancelService;

    @BeforeEach
    void startOtherServer() {
        if (Objects.isNull(otherServer)) {
            // 11장: 결제 이벤트 토픽은 컨텍스트마다 이름이 달라(OrderServer) 다른 서버에도 같은 토픽을 넘긴다
            otherServer = OtherPaymentServer.start(mysql, mockPg, List.of("payment.events.topic=" + orderServer.topic()), SkipLockedOutboxRelay.class);
        }
    }

    @AfterAll
    static void stopOtherServer() {
        if (Objects.nonNull(otherServer)) {
            otherServer.close();
            otherServer = null;
        }
    }

    @Test
    void measure() {
        measure("작업 잠금으로 한 대만(운영)", server -> server.getBean(ExclusiveJobRunner.class)
                .run(ExclusiveJobRunner.OUTBOX, server.getBean(PaymentOutboxRelay.class)::relay));
        measure("잠그지 않음(서버마다 릴레이)", server -> server.getBean(PaymentOutboxRelay.class).relay());
        measure("한 줄씩 잠가 나눠 보내기(skip locked)", server -> server.getBean(SkipLockedOutboxRelay.class).relay());
    }

    private void measure(String name, Consumer<ApplicationContext> relay) {
        // 첫 묶음이 연결을 여는 값을 치르지 않게 작은 묶음을 한 번 돌려 버린다
        drain(pile(5), relay);
        for (int round = 1; round <= ROUNDS; round++) {
            List<String> orderIds = pile(PAYMENTS);
            long drainedMillis = drain(orderIds, relay);
            awaitSettled(orderIds);
            OutboxMeasurementLog.write("릴레이 " + name + " | #" + round + " | 서버 2대 | 보낼 결과 " + PAYMENTS * 2 + " | 다 보낸 시간 "
                    + drainedMillis + "ms · 처리량 " + Math.round(PAYMENTS * 2 * 1000.0 / Math.max(1, drainedMillis)) + "건/초 | "
                    + OutboxMeasurementLog.describe(orderServer, orderIds));
        }
    }

    // 결제를 승인 뒤 전액 취소해 보낼 결과를 쌓는다(릴레이는 꺼져 있다)
    private List<String> pile(int payments) {
        jdbcTemplate.update("delete from payment_outbox");
        return IntStream.range(0, payments).mapToObj(each -> {
            String orderId = "order-" + UUID.randomUUID();
            paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
            paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
            paymentCancelService.cancel(new CancelPaymentCommand(orderId, "cancel-" + orderId, null, "Customer changed their mind"));
            return orderId;
        }).toList();
    }

    // 두 서버가 저마다 보낼 것이 없어질 때까지 릴레이를 쉬지 않고 돈다. 둘 다 멈춘 때까지 걸린 시간
    private long drain(List<String> orderIds, Consumer<ApplicationContext> relay) {
        long begin = System.nanoTime();
        List<CompletableFuture<Void>> running = List.of(thisServer, otherServer).stream()
                .map(server -> CompletableFuture.runAsync(() -> {
                    while (unsent() > 0) {
                        relay.accept(server);
                    }
                }))
                .toList();
        running.forEach(CompletableFuture::join);
        return (System.nanoTime() - begin) / 1_000_000;
    }

    private int unsent() {
        Integer unsent = jdbcTemplate.queryForObject("select count(*) from payment_outbox where sent_at is null", Integer.class);
        return Objects.requireNonNullElse(unsent, 0);
    }

    private void awaitSettled(List<String> orderIds) {
        int[] last = {-1};
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(2)).until(() -> {
            int received = orderIds.stream().mapToInt(orderId -> orderServer.receipts(orderId).size()).sum();
            boolean settled = received == last[0] && received >= orderIds.size() * 2;
            last[0] = received;
            return settled;
        });
    }
}
