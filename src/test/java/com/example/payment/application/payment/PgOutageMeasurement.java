package com.example.payment.application.payment;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.zaxxer.hikari.HikariDataSource;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 12장 측정(./gradlew operationsMeasurement — 기본 check 에서 뺐다). 카드사 장애 동안 방식마다(시간 제한만, 다시 시도, 차단기) 같은 부하를 보낸다.
 * 장애: 카드사가 승인 요청을 받고 30초 동안 답하지 않다가 아무것도 남기지 않는다(LOST). PG 시간 제한은 운영 값(연결 1초, 읽기 5초),
 * 차단기도 운영 값(최근 20번 중 50%, 최소 10번, 30초)이다. 고객 20명이 장애 15초 동안 쉬지 않고 새 결제를 준비하고 승인을 요청한다
 * (앞 요청의 답을 받으면 바로 다음 요청). 15초가 지나면 새 요청을 멈추고 보낸 요청이 모두 끝날 때까지 기다린다.
 * 응답 시간: 승인 요청 하나가 답(실패 포함)을 받기까지(중앙·p95·최대). DB 연결: 결제 서버 연결 풀(HikariCP, 10개)의 쓰는 중인 연결 수를 5ms 마다 읽은 최댓값과 평균.
 * 결과 모름: 이 측정이 만든 결제 가운데 APPROVAL_UNKNOWN 으로 남은 수. 카드사 승인 요청: 카드사가 받은 승인 요청 수.
 * 결과는 build/measurement/ch12-operations.txt 에 덧붙인다.
 */
@Tag("operations-measurement")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class PgOutageMeasurement {

    static final Path OUTPUT = Path.of("build", "measurement", "ch12-operations.txt");
    static final Duration OUTAGE = Duration.ofSeconds(15);
    static final Duration PG_SILENCE = Duration.ofSeconds(30);
    static final int CUSTOMERS = 20;
    static final BigDecimal PREPARED = new BigDecimal("15000");

    @Autowired
    MockPgServer mockPg;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

    @Autowired
    CircuitBreaker pgApprovalCircuitBreaker;

    abstract String method();

    @Test
    @DisplayName("카드사 장애 15초 동안 고객 20명이 승인을 요청하면 응답 시간·쓰는 중인 DB 연결·결과 모름 수를 남긴다")
    void measureOutage() throws InterruptedException {
        pgApprovalCircuitBreaker.reset();
        // 주문번호는 64자까지다
        String batch = "outage-" + UUID.randomUUID().toString().substring(0, 8);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        Map<String, AtomicInteger> answers = new ConcurrentHashMap<>();
        AtomicInteger maxConnections = new AtomicInteger();
        AtomicLong connectionSum = new AtomicLong();
        AtomicLong samples = new AtomicLong();
        AtomicReference<Boolean> sampling = new AtomicReference<>(true);
        Thread sampler = new Thread(() -> sampleConnections(maxConnections, connectionSum, samples, sampling));
        sampler.start();
        mockPg.loseAfter(PG_SILENCE);
        long until = System.nanoTime() + OUTAGE.toNanos();
        ExecutorService customers = Executors.newFixedThreadPool(CUSTOMERS);
        CountDownLatch done = new CountDownLatch(CUSTOMERS);
        for (int customer = 0; customer < CUSTOMERS; customer++) {
            customers.submit(() -> {
                try {
                    while (System.nanoTime() < until) {
                        latencies.add(confirmOnce(batch, answers));
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(5, TimeUnit.MINUTES);
        customers.shutdown();
        sampling.set(false);
        sampler.join();
        mockPg.reset();
        write(method() + " | 장애 " + OUTAGE.toSeconds() + "초·고객 " + CUSTOMERS + "명 | 승인 요청 " + latencies.size()
                + " | 응답 ms 중앙 " + percentile(latencies, 50) + "·p95 " + percentile(latencies, 95) + "·최대 " + percentile(latencies, 100)
                + " | 쓰는 중인 DB 연결 최대 " + maxConnections.get() + "/10·평균 "
                + String.format("%.2f", (double) connectionSum.get() / Math.max(samples.get(), 1))
                + " | 결과 모름 " + count(batch, "APPROVAL_UNKNOWN") + " | 승인 실패 " + count(batch, "APPROVAL_FAILED")
                + " | 카드사가 받은 승인 요청 " + cardCompanyRequests(batch) + " | 답 " + new TreeMap<>(answers));
    }

    private long confirmOnce(String batch, Map<String, AtomicInteger> answers) {
        String orderId = batch + "-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        long started = System.nanoTime();
        String answer = "APPROVED";
        try {
            paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        } catch (RuntimeException refused) {
            answer = refused.getClass().getSimpleName();
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        answers.computeIfAbsent(answer, key -> new AtomicInteger()).incrementAndGet();
        return elapsed;
    }

    private void sampleConnections(AtomicInteger max, AtomicLong sum, AtomicLong samples, AtomicReference<Boolean> sampling) {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        while (sampling.get()) {
            int active = hikari.getHikariPoolMXBean().getActiveConnections();
            max.accumulateAndGet(active, Math::max);
            sum.addAndGet(active);
            samples.incrementAndGet();
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private int count(String batch, String status) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from payment where order_id like ? and status = ?", Integer.class,
                batch + "-%", status);
        return Objects.requireNonNullElse(count, 0);
    }

    private int cardCompanyRequests(String batch) {
        return jdbcTemplate.queryForList("select order_id from payment where order_id like ?", String.class, batch + "-%").stream()
                .mapToInt(mockPg::confirmRequestCount)
                .sum();
    }

    static long percentile(List<Long> values, int percent) {
        List<Long> sorted = values.stream().sorted().collect(Collectors.toList());
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(index, 0));
    }

    static synchronized void write(String line) {
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
