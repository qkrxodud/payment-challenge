package com.example.payment.application.payment;

import com.example.payment.InventoryServerConfiguration;
import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.stock.StockReservations;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 10장 결정 1 측정: 재고 예약 한 건이 재고 서비스에서 얼마나 걸리는가(시간 제한을 정한 근거). 같은 상품(재고 넉넉)에 예약 요청을
 * (가) 하나씩 50건, (나) 스레드 300개에서 한꺼번에 300건 보내 결제 서버 쪽에서 잰 응답 시간을 센다. 시간 제한은 10초로 늘려 잘리지 않게 한다.
 * 3번 돈다. 결과는 build/measurement/ch10-saga.txt 에 덧붙인다.
 */
@Tag("saga-measurement")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "payment.inventory.read-timeout=10s")
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InventoryServerConfiguration.class})
class ReservationLatencyMeasurement extends OrderPaymentContract {

    private static final Path OUTPUT = Path.of("build", "measurement", "ch10-saga.txt");
    private static final int SEQUENTIAL = 50;
    private static final int BURST = 300;
    private static final int ROUNDS = 3;

    @Autowired
    StockReservations stockReservations;

    private long nextReservation = 1_000_000L;

    @Test
    void measure() {
        Long productId = shop.product(PRICE, 100_000);
        List<PaymentItem> items = List.of(PaymentItem.of(productId, 1, PRICE));
        IntStream.range(0, 20).forEach(index -> reserve(items));
        for (int round = 1; round <= ROUNDS; round++) {
            List<Long> sequential = IntStream.range(0, SEQUENTIAL).mapToObj(index -> reserve(items)).toList();
            List<Long> burst = burst(items);
            write("예약 응답 시간 | #" + round + " | 하나씩 " + SEQUENTIAL + "건 ms " + stats(sequential)
                    + " | 한꺼번에 " + BURST + "건 ms " + stats(burst)
                    + " | 1초 넘음 " + burst.stream().filter(millis -> millis > 1000).count() + "건");
        }
    }

    private synchronized long nextId() {
        return nextReservation++;
    }

    private long reserve(List<PaymentItem> items) {
        long id = nextId();
        long begin = System.nanoTime();
        stockReservations.reserve(id, "latency-" + id, items);
        return (System.nanoTime() - begin) / 1_000_000;
    }

    private List<Long> burst(List<PaymentItem> items) {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(BURST);
        try {
            List<CompletableFuture<Long>> answers = IntStream.range(0, BURST)
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> {
                        awaitStart(start);
                        return reserve(items);
                    }, executor))
                    .toList();
            start.countDown();
            return answers.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdown();
        }
    }

    private static String stats(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return "중앙 " + sorted.get(sorted.size() / 2) + "·p95 " + sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1)
                + "·p99 " + sorted.get((int) Math.ceil(sorted.size() * 0.99) - 1) + "·최대 " + sorted.get(sorted.size() - 1);
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
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
}
