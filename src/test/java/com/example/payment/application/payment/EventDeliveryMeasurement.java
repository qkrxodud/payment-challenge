package com.example.payment.application.payment;

import static org.awaitility.Awaitility.await;

import com.example.payment.BrokerLink;
import com.example.payment.KafkaBroker;
import com.example.payment.MockPgConfiguration;
import com.example.payment.OrderServer;
import com.example.payment.OrderServerConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 9장 측정(./gradlew outboxMeasurement): 결제 60건을 스레드 3개에서 0.2초 간격으로 승인하고 바로 전액 취소하는 동안, 시작 1초 뒤부터 4초 동안
 * 브로커를 끊는다(보내기 시간 제한 3초보다 길게). 끝난 뒤 보낼 것이 남지 않고 주문 서비스가 받은 수가 3초 동안 그대로일 때까지 기다려,
 * 보내는 방식마다 유실·중복·순서 위반·주문 상태 어긋남·도착 지연과 승인·취소 응답 시간을 센다. 묶음마다 3번(#1~#3), 앞에 끊지 않은 작은 묶음을 한 번 돌려 버린다.
 * 키 없이 보내는 방식도 기본 파티셔너를 그대로 쓴다(계약 테스트처럼 돌아가며 고르지 않음).
 * 구현마다 이 클래스를 상속하고 보내는 방식만 바꿔 끼운다(PaymentEventContract 와 같다). 릴레이는 0.1초마다 돈다.
 */
@Tag("outbox-measurement")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.outbox.enabled=true",
                "payment.outbox.interval=100ms"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, OrderServerConfiguration.class})
abstract class EventDeliveryMeasurement {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final int PAYMENTS = 60;
    static final int THREADS = 3;
    static final Duration PACE = Duration.ofMillis(200);
    static final Duration CUT_AFTER = Duration.ofSeconds(1);
    static final Duration CUT = Duration.ofSeconds(4);
    static final int ROUNDS = 3;

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

    BrokerLink broker = KafkaBroker.link();

    abstract String name();

    /** 아직 브로커에 보내지 않은 이벤트 수. 바로 보내는 방식은 남겨 두는 것이 없다 */
    int pendingEvents() {
        return 0;
    }

    @Test
    void measure() {
        run(6, false);
        for (int round = 1; round <= ROUNDS; round++) {
            OutboxMeasurementLog.write(name() + " | #" + round + " | " + run(PAYMENTS, true));
        }
    }

    private String run(int payments, boolean cutBroker) {
        List<String> orderIds = IntStream.range(0, payments).mapToObj(each -> prepare()).toList();
        ConcurrentLinkedQueue<Long> confirms = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> cancels = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        long begin = System.nanoTime();
        CompletableFuture<Void> cutting = cutBroker ? CompletableFuture.runAsync(() -> cutFor(begin)) : CompletableFuture.completedFuture(null);
        try {
            List<CompletableFuture<Void>> running = IntStream.range(0, THREADS)
                    .mapToObj(thread -> CompletableFuture.runAsync(() -> payInTurn(orderIds, thread, confirms, cancels), executor))
                    .toList();
            running.forEach(CompletableFuture::join);
            cutting.join();
        } finally {
            executor.shutdown();
            broker.restore();
        }
        long trafficMillis = (System.nanoTime() - begin) / 1_000_000;
        awaitSettled(orderIds);
        return OutboxMeasurementLog.describe(orderServer, orderIds)
                + " | 승인 응답 ms " + OutboxMeasurementLog.percentiles(List.copyOf(confirms))
                + " | 취소 응답 ms " + OutboxMeasurementLog.percentiles(List.copyOf(cancels))
                + " | 결제를 보낸 시간 " + trafficMillis + "ms";
    }

    // 이 스레드 몫(번호 % THREADS)의 결제를 0.2초 간격으로 승인하고 바로 전액 취소한다
    private void payInTurn(List<String> orderIds, int thread, ConcurrentLinkedQueue<Long> confirms, ConcurrentLinkedQueue<Long> cancels) {
        for (int index = thread; index < orderIds.size(); index += THREADS) {
            String orderId = orderIds.get(index);
            long started = System.nanoTime();
            paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
            confirms.add((System.nanoTime() - started) / 1_000_000);
            started = System.nanoTime();
            paymentCancelService.cancel(new CancelPaymentCommand(orderId, "cancel-" + orderId, null, "Customer changed their mind"));
            cancels.add((System.nanoTime() - started) / 1_000_000);
            sleep(PACE.toMillis());
        }
    }

    private void cutFor(long begin) {
        sleep(CUT_AFTER.toMillis() - (System.nanoTime() - begin) / 1_000_000);
        broker.cut();
        sleep(CUT.toMillis());
        broker.restore();
    }

    // 보낼 것이 남지 않고, 주문 서비스가 받은 수가 3초 동안 그대로일 때까지(길게는 2분)
    private void awaitSettled(List<String> orderIds) {
        await().atMost(Duration.ofMinutes(2)).until(() -> pendingEvents() == 0);
        int[] last = {-1};
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(3)).until(() -> {
            int received = orderIds.stream().mapToInt(orderId -> orderServer.receipts(orderId).size()).sum();
            boolean settled = received == last[0];
            last[0] = received;
            return settled;
        });
    }

    private String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    private static void sleep(long millis) {
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
