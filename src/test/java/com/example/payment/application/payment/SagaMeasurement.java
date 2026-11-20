package com.example.payment.application.payment;

import com.example.payment.Attempt;
import com.example.payment.InventoryServerConfiguration;
import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 10장 측정: 재고 서비스를 늦추고 끊은 동안 방식마다 결제 응답 시간, 넘겨 판 수량, 끝내 돌아오지 않은 재고, 원복까지 걸린 시간을 센다.
 * 한 번 돌 때: 재고 20개 상품에 (가) 보통 주문 10건 → (나) 재고 서비스가 답을 1.5초 늦게 주는 동안 10건(시간 제한은 운영 값 1초)
 * → (다) 재고 서비스가 멈춘 동안(중계 끊음 + 재고 명령 읽기 멈춤) 10건과 승인된 결제 5건 전액 취소 → 재고 서비스를 되돌린 뒤 20초까지 기다린다.
 * 주문은 한 건씩 차례로 보낸다(동시 몰림은 SagaContract 몰림 계약이 본다). 릴레이는 운영처럼 0.5초마다 돈다.
 * - 넘겨 판 수량: 끝난 뒤 승인이 남은 결제의 수량 − 처음 재고(0 보다 작으면 0). 승인 답 수: 고객이 받은 승인 답(뒤에 보상 취소된 것 포함).
 * - 어긋난 재고: 20초 뒤 (남은 재고 + 승인이 남은 결제의 수량) − 처음 재고. 음수면 끝내 돌아오지 않은 재고, 양수면 처음보다 늘어난 재고.
 * - 맞춰진 때: 재고 서비스를 되돌린 때부터 남은 재고 + 승인 수량이 처음 재고와 같아질 때까지(20초 안에 안 되면 "안 맞음").
 * - 원복 시간: 승인 없이 끝나거나 취소된 결제마다 그 승인·취소 요청을 보낸 때부터 재고 서비스가 그 예약을 해제한 때까지(예약 기록이 있는 방식만).
 *   (결제 결과를 남긴 때를 기준으로 하면, 그 자리에서 HTTP 로 해제하는 비교는 결과를 남기기 전에 해제해 음수가 나온다 — 첫 실행에서 봄)
 * 결과는 build/measurement/ch10-saga.txt 에 덧붙인다. 방식마다 3번 돈다.
 */
@Tag("saga-measurement")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.outbox.enabled=true",
                "payment.outbox.interval=500ms"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InventoryServerConfiguration.class})
abstract class SagaMeasurement extends OrderPaymentContract {

    private static final Path OUTPUT = Path.of("build", "measurement", "ch10-saga.txt");
    private static final int STOCK = 20;
    private static final int ORDERS_PER_PHASE = 10;
    private static final int CANCELS = 5;
    private static final int ROUNDS = 3;
    private static final Duration SLOW = Duration.ofMillis(1500);
    private static final Duration SETTLE_LIMIT = Duration.ofSeconds(20);

    @Autowired
    PaymentCancelService paymentCancelService;

    // 이번 한 번에서 고객이 받은 승인 답 수
    private int approvedAnswers;

    // 주문마다 마지막으로 승인·취소 요청을 보낸 때(밀리초)
    private final Map<String, Long> requestedAt = new HashMap<>();

    /** 기록에 남길 방식 이름 */
    abstract String name();

    @Test
    void measure() {
        orderAndConfirm(order(shop.product(PRICE, 5), 1));
        for (int round = 1; round <= ROUNDS; round++) {
            write(name() + " | #" + round + " | " + round());
        }
    }

    private String round() {
        approvedAnswers = 0;
        Long productId = shop.product(PRICE, STOCK);
        List<String> orderIds = new ArrayList<>();
        List<Long> normal = phase(productId, orderIds);
        inventory.link().slowAnswers(SLOW);
        List<Long> slow = phase(productId, orderIds);
        inventory.link().slowAnswers(Duration.ZERO);
        inventory.down();
        List<Long> down = phase(productId, orderIds);
        cancelSome(orderIds);
        long upAt = System.nanoTime();
        inventory.up();
        String settled = awaitSettled(productId, upAt);
        int approvedQuantity = approvedQuantityOf(productId);
        int off = inventory.stockOf(productId) + approvedQuantity - STOCK;
        return "응답 ms 보통 " + Stats.of(normal) + " / 늦음 " + Stats.of(slow) + " / 멈춤 " + Stats.of(down)
                + " | 승인 답 " + approvedAnswers + " | 넘겨 판 수량 " + Math.max(0, approvedQuantity - STOCK)
                + " | 어긋난 재고 " + off + " | 맞춰진 때 " + settled + " | 원복 ms " + restoreTimes(orderIds);
    }

    private List<Long> phase(Long productId, List<String> orderIds) {
        List<Long> elapsed = new ArrayList<>();
        for (int index = 0; index < ORDERS_PER_PHASE; index++) {
            PreparePaymentCommand command = order(productId, 1);
            orderIds.add(command.orderId());
            requestedAt.put(command.orderId(), System.currentTimeMillis());
            long begin = System.nanoTime();
            Attempt<PaymentResult> answer = orderAndConfirm(command);
            elapsed.add((System.nanoTime() - begin) / 1_000_000);
            if (answer.isAnswered() && answer.result().status() == PaymentStatus.APPROVED) {
                approvedAnswers++;
            }
        }
        return elapsed;
    }

    private void cancelSome(List<String> orderIds) {
        orderIds.stream()
                .filter(orderId -> paymentQueryService.find(orderId).status() == PaymentStatus.APPROVED)
                .limit(CANCELS)
                .forEach(this::cancel);
    }

    private void cancel(String orderId) {
        requestedAt.put(orderId, System.currentTimeMillis());
        paymentCancelService.cancel(new CancelPaymentCommand(orderId, "cancel-" + orderId, null, "Customer changed their mind"));
    }

    private String awaitSettled(Long productId, long upAt) {
        long deadline = upAt + SETTLE_LIMIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (inventory.stockOf(productId) + approvedQuantityOf(productId) == STOCK) {
                return (System.nanoTime() - upAt) / 1_000_000 + "ms";
            }
            sleep(Duration.ofMillis(100));
        }
        return "안 맞음";
    }

    private String restoreTimes(List<String> orderIds) {
        List<Long> times = new ArrayList<>();
        for (String orderId : orderIds) {
            Long paymentId = jdbcTemplate.queryForObject("select id from payment where order_id = ?", Long.class, orderId);
            Long closedAt = requestedAt.get(orderId);
            Long released = inventory.releasedAtMillis(paymentId);
            if (Objects.nonNull(closedAt) && Objects.nonNull(released)) {
                times.add(released - closedAt);
            }
        }
        if (times.isEmpty()) {
            return "기록 없음";
        }
        return Stats.of(times) + " (" + times.size() + "건)";
    }

    int approvedQuantityOf(Long product) {
        Integer quantity = jdbcTemplate.queryForObject("select coalesce(sum(i.quantity), 0) from payment_item i "
                        + "join payment p on p.id = i.payment_id where p.status in ('APPROVED', 'CAPTURED') and i.product_id = ?",
                Integer.class, product);
        return Objects.requireNonNullElse(quantity, 0);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void write(String line) {
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println("[ch10-saga] " + line);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** 중앙값·p95·최대 */
    static class Stats {

        static String of(List<Long> values) {
            List<Long> sorted = values.stream().sorted().toList();
            return "중앙 " + sorted.get(sorted.size() / 2) + "·p95 " + sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1)
                    + "·최대 " + sorted.get(sorted.size() - 1);
        }
    }
}
