package com.example.payment.application.payment;

import com.example.payment.InventoryServerConfiguration;
import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 10장 결정 1 측정: 재고 서비스에 새 연결을 맺는 데 얼마나 걸리는가(연결 시간 제한 300ms 를 정한 근거). 재고 서비스(Tomcat) 포트에 직접
 * TCP 연결을 (가) 하나씩 50번, (나) 스레드 300개·(다) 1000개에서 한꺼번에 맺고 연결이 맺어질 때까지 걸린 시간을 잰다(연결 시간 제한 10초, 맺은 연결은 바로 닫는다).
 * 시험 JVM 과 재고 서비스가 같은 머신이라 망을 건너는 시간은 들어 있지 않다. 3번 돈다. 결과는 build/measurement/ch10-connect.txt 에 덧붙인다.
 */
@Tag("saga-measurement")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InventoryServerConfiguration.class})
class InventoryConnectMeasurement extends OrderPaymentContract {

    private static final Path OUTPUT = Path.of("build", "measurement", "ch10-connect.txt");
    private static final int SEQUENTIAL = 50;
    private static final int ROUNDS = 3;
    private static final int TIMEOUT_MILLIS = 10_000;

    @Test
    void measure() {
        IntStream.range(0, 20).forEach(index -> connect());
        for (int round = 1; round <= ROUNDS; round++) {
            List<Long> sequential = IntStream.range(0, SEQUENTIAL).mapToObj(index -> connect()).toList();
            List<Long> burst300 = burst(300);
            List<Long> burst1000 = burst(1000);
            write("연결 시간 | #" + round + " | 하나씩 " + SEQUENTIAL + "번 ms " + stats(sequential)
                    + " | 한꺼번에 300 ms " + stats(burst300) + " · 300ms 넘음 " + over(burst300)
                    + " | 한꺼번에 1000 ms " + stats(burst1000) + " · 300ms 넘음 " + over(burst1000));
        }
    }

    // 연결을 맺는 데 걸린 시간(ms). 맺지 못하면 -1
    private long connect() {
        long begin = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), inventory.serverPort()), TIMEOUT_MILLIS);
            return (System.nanoTime() - begin) / 1_000_000;
        } catch (IOException failed) {
            return -1;
        }
    }

    private List<Long> burst(int count) {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            List<CompletableFuture<Long>> answers = IntStream.range(0, count)
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> {
                        awaitStart(start);
                        return connect();
                    }, executor))
                    .toList();
            start.countDown();
            return answers.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdown();
        }
    }

    private static String over(List<Long> values) {
        long failed = values.stream().filter(millis -> millis < 0).count();
        return values.stream().filter(millis -> millis > 300).count() + "건, 실패 " + failed + "건";
    }

    private static String stats(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values.stream().filter(millis -> millis >= 0).sorted().toList());
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
            System.out.println("[ch10-connect] " + line);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
