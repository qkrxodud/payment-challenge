package com.example.payment.application.payment;

import com.example.payment.Attempt;
import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.global.error.ErrorCode;
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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 7장 결정 1 측정: 같은 결제에 동시 요청 N개를 한꺼번에 보내고(시험이 트리거로 겹침을 고정하지 않는다) 잠금 방식마다 응답 시간과 답을 센다.
 * - 취소: 승인된 15000원 결제에 1000원 부분 취소 N개(Idempotency-Key 는 모두 다름). 15개까지만 남은 금액 안이다.
 * - 승인: 준비한 결제에 같은 결제 키 승인 N개.
 * N 은 10, 30. 묶음마다 3번(결제 3건) 돌린다. 카드사는 바로 답한다. 결과는 build/measurement/ch07-concurrency.txt 에 덧붙인다.
 * 구현마다 이 클래스를 상속하고, 결제를 바꾸려고 읽는 저장소만 바꿔 끼운다(PaymentLockContract 와 같다).
 */
@Tag("concurrency-measurement")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class LockMeasurement {

    private static final Path OUTPUT = Path.of("build", "measurement", "ch07-concurrency.txt");
    private static final BigDecimal PREPARED = new BigDecimal("15000");
    private static final BigDecimal SMALL_PART = new BigDecimal("1000");
    private static final List<Integer> SIZES = List.of(10, 30);
    private static final int ROUNDS = 3;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentCancelService paymentCancelService;

    @Autowired
    PaymentQueryService paymentQueryService;

    /** 기록에 남길 구현 이름 */
    abstract String name();

    @Test
    void measure() {
        // 첫 묶음이 연결·클래스 적재를 치르지 않게 한 번 돌려 버린다
        cancelsTogether(approvedOrder(), 5);
        SIZES.forEach(size -> IntStream.rangeClosed(1, ROUNDS).forEach(round -> {
            String orderId = approvedOrder();
            List<Timed<CancelResult>> cancels = cancelsTogether(orderId, size);
            write(String.format("%s | 취소 N=%d #%d | %s | 응답 ms %s | 카드사 취소 요청 %d, 취소 %d, 카드사 잔액 %s, 우리 남은 금액 %s",
                    name(), size, round, answers(cancels), times(cancels), mockPg.cancelRequestCount(orderId),
                    mockPg.cancelCount(orderId), mockPg.balanceAmount(orderId).stripTrailingZeros().toPlainString(),
                    balanceOf(orderId).stripTrailingZeros().toPlainString()));
        }));
        SIZES.forEach(size -> IntStream.rangeClosed(1, ROUNDS).forEach(round -> {
            String orderId = prepare();
            List<Timed<PaymentResult>> confirms = confirmsTogether(orderId, size);
            write(String.format("%s | 같은 키 승인 N=%d #%d | %s | 응답 ms %s | 카드사 승인 요청 %d, 결제 상태 %s",
                    name(), size, round, answers(confirms), times(confirms), mockPg.confirmRequestCount(orderId),
                    paymentQueryService.find(orderId).status()));
        }));
    }

    List<Timed<CancelResult>> cancelsTogether(String orderId, int size) {
        return together(size, each -> () -> paymentCancelService.cancel(new CancelPaymentCommand(orderId,
                "cancel-" + each + "-" + orderId, SMALL_PART, "Customer changed their mind")));
    }

    List<Timed<PaymentResult>> confirmsTogether(String orderId, int size) {
        return together(size, each -> () -> paymentConfirmService.confirm(
                new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED)));
    }

    // 요청 N개를 스레드 N개에 올려 두고 한꺼번에 출발시킨다
    static <T> List<Timed<T>> together(int size, Function<Integer, Supplier<T>> requests) {
        ExecutorService threads = Executors.newFixedThreadPool(size);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Timed<T>>> running = IntStream.range(0, size)
                    .mapToObj(each -> CompletableFuture.supplyAsync(() -> {
                        awaitStart(start);
                        long begin = System.nanoTime();
                        Attempt<T> attempt = Attempt.of(requests.apply(each));
                        return new Timed<>(attempt, (System.nanoTime() - begin) / 1_000_000);
                    }, threads))
                    .toList();
            start.countDown();
            return running.stream().map(CompletableFuture::join).toList();
        } finally {
            threads.shutdown();
        }
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    // 답 종류별 개수: 답한 것은 결과 상태, 받지 않은 것은 오류 코드
    static <T> String answers(List<Timed<T>> timed) {
        Map<String, Long> counts = timed.stream()
                .collect(Collectors.groupingBy(each -> each.label(), TreeMap::new, Collectors.counting()));
        return counts.toString();
    }

    static <T> String times(List<Timed<T>> timed) {
        List<Long> millis = new ArrayList<>(timed.stream().map(Timed::millis).toList());
        Collections.sort(millis);
        return String.format("중앙 %d, 최대 %d", millis.get(millis.size() / 2), millis.get(millis.size() - 1));
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    String approvedOrder() {
        String orderId = prepare();
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        return orderId;
    }

    BigDecimal balanceOf(String orderId) {
        return PREPARED.subtract(paymentQueryService.findCancels(orderId).stream()
                .filter(cancel -> cancel.cancelStatus().isDone())
                .map(CancelResult::cancelAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    static void write(String line) {
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** 요청 하나의 결과와 걸린 시간 */
    static class Timed<T> {

        private final Attempt<T> attempt;
        private final long millis;

        Timed(Attempt<T> attempt, long millis) {
            this.attempt = attempt;
            this.millis = millis;
        }

        long millis() {
            return millis;
        }

        String label() {
            ErrorCode errorCode = attempt.errorCode();
            if (Objects.nonNull(errorCode)) {
                return errorCode.name();
            }
            return String.valueOf(statusOf(attempt.result()));
        }

        private static Object statusOf(Object result) {
            if (result instanceof CancelResult cancel) {
                return cancel.cancelStatus();
            }
            if (result instanceof PaymentResult payment) {
                return payment.status();
            }
            return result;
        }
    }
}
