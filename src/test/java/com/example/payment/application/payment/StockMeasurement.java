package com.example.payment.application.payment;

import com.example.payment.Attempt;
import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.Shop;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.OrderLine;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.payment.PaymentStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 8장 결정 3 측정: 재고 100개인 상품 하나에 주문 300건의 승인 요청을 스레드 300개에서 래치로 한꺼번에 보내고(겹침을 고정하지 않는다)
 * 재고를 줄이는 방식마다 승인 수, 과판매 수(승인 수 − 처음 재고, 0 보다 작으면 0), 남은 재고, 카드사 승인 수, 답별 개수, 응답 시간, 처리량을 센다.
 * 주문은 미리 준비해 두고 승인만 한꺼번에 보낸다. 카드사는 바로 답한다. 묶음마다 3번 돌린다. 결과는 build/measurement/ch08-stock.txt 에 덧붙인다.
 * 구현마다 이 클래스를 상속하고, 재고 저장소(와 변경 번호 비교는 승인 서비스)만 바꿔 끼운다(StockContract 와 같다).
 */
@Tag("stock-measurement")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class StockMeasurement {

    private static final Path OUTPUT = Path.of("build", "measurement", "ch08-stock.txt");
    private static final BigDecimal PRICE = new BigDecimal("10000");
    private static final int STOCK = 100;
    private static final int ORDERS = 300;
    private static final int ROUNDS = 3;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    /** 기록에 남길 구현 이름 */
    abstract String name();

    PaymentResult confirm(ConfirmPaymentCommand command) {
        return paymentConfirmService.confirm(command);
    }

    /** 이번 묶음에서 다시 시도한 횟수. 다시 시도하지 않는 구현은 비어 있다 */
    String retries() {
        return "";
    }

    @Test
    void measure() {
        Shop shop = new Shop(jdbcTemplate);
        // 첫 묶음이 연결·클래스 적재를 치르지 않게 작은 묶음을 한 번 돌려 버린다
        burst(shop, 5, 20);
        retries();
        for (int round = 1; round <= ROUNDS; round++) {
            Long productId = shop.product(PRICE, STOCK);
            Burst burst = burst(shop, productId, ORDERS);
            write(name() + " | #" + round + " | " + burst.describe(STOCK, shop.stockOf(productId)) + retries());
        }
    }

    private Burst burst(Shop shop, int stock, int orders) {
        return burst(shop, shop.product(PRICE, stock), orders);
    }

    private Burst burst(Shop shop, Long productId, int orders) {
        List<PaymentResult> prepared = IntStream.range(0, orders)
                .mapToObj(index -> paymentPrepareService.prepare(new PreparePaymentCommand("order-" + UUID.randomUUID(),
                        PRICE, null, List.of(new OrderLine(productId, 1)), null, null)))
                .toList();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(orders);
        try {
            List<CompletableFuture<Timed>> answers = prepared.stream()
                    .map(payment -> CompletableFuture.supplyAsync(() -> timed(start, payment), executor))
                    .toList();
            long begin = System.nanoTime();
            start.countDown();
            List<Timed> timed = answers.stream().map(CompletableFuture::join).toList();
            long elapsedMillis = (System.nanoTime() - begin) / 1_000_000;
            int cardApprovals = prepared.stream().mapToInt(payment -> mockPg.approvalCount(payment.orderId())).sum();
            return new Burst(timed, elapsedMillis, cardApprovals);
        } finally {
            executor.shutdown();
        }
    }

    private Timed timed(CountDownLatch start, PaymentResult payment) {
        awaitStart(start);
        long begin = System.nanoTime();
        Attempt<PaymentResult> answer = Attempt.of(() -> confirm(
                new ConfirmPaymentCommand(payment.orderId(), "key-" + payment.orderId(), payment.amount())));
        return new Timed(answer, (System.nanoTime() - begin) / 1_000_000);
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
            System.out.println("[ch08-stock] " + line);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** 승인 요청 하나의 답과 걸린 시간 */
    static class Timed {

        final Attempt<PaymentResult> answer;
        final long millis;

        Timed(Attempt<PaymentResult> answer, long millis) {
            this.answer = answer;
            this.millis = millis;
        }

        String outcome() {
            if (answer.isAnswered()) {
                return answer.result().status().name();
            }
            return Objects.requireNonNull(answer.errorCode(), ErrorCode.INTERNAL_ERROR.name()).toString();
        }

        boolean isApproved() {
            return answer.isAnswered() && answer.result().status() == PaymentStatus.APPROVED;
        }
    }

    /** 한 묶음의 결과 */
    static class Burst {

        final List<Timed> timed;
        final long elapsedMillis;
        final int cardApprovals;

        Burst(List<Timed> timed, long elapsedMillis, int cardApprovals) {
            this.timed = timed;
            this.elapsedMillis = elapsedMillis;
            this.cardApprovals = cardApprovals;
        }

        String describe(int stock, int left) {
            long approved = timed.stream().filter(Timed::isApproved).count();
            Map<String, Long> outcomes = new TreeMap<>(timed.stream()
                    .collect(Collectors.groupingBy(Timed::outcome, Collectors.counting())));
            List<Long> millis = new ArrayList<>(timed.stream().map(time -> time.millis).toList());
            Collections.sort(millis);
            return "재고 " + stock + " · 주문 " + timed.size()
                    + " | 승인 " + approved + " | 과판매 " + Math.max(0, approved - stock)
                    + " | 남은 재고 " + left + "(처음 − 승인 = " + (stock - approved) + ")"
                    + " | 카드사 승인 " + cardApprovals
                    + " | 답 " + outcomes
                    + " | 응답 ms 중앙 " + percentile(millis, 50) + " · p95 " + percentile(millis, 95)
                    + " · 최대 " + millis.get(millis.size() - 1)
                    + " | 전체 " + elapsedMillis + "ms · 처리량 " + Math.round(timed.size() * 1000.0 / elapsedMillis) + "건/초";
        }

        private static long percentile(List<Long> sorted, int percent) {
            int index = (int) Math.ceil(sorted.size() * percent / 100.0) - 1;
            return sorted.get(Math.max(0, index));
        }
    }
}
