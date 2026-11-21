package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.DeadLetter;
import com.example.payment.DeadLetterTopic;
import com.example.payment.InventoryServer;
import com.example.payment.InventoryServerConfiguration;
import com.example.payment.KafkaBroker;
import com.example.payment.MessageFeed;
import com.example.payment.MockPgConfiguration;
import com.example.payment.ReceiverOutage;
import com.example.payment.TestcontainersConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 11장: 재고 명령(예약 확정·해제)을 받는 재고 서비스(소비자)의 규칙. 주문 서비스 계약(PaymentEventConsumerContract)과 같은 문제를 같은 방법으로 본다 —
 * 같은 명령이 다시 오거나, 새 명령 뒤에 옛 명령이 오거나, 처리할 수 없는 명령이 오거나, 재고 서비스 DB 가 잠시 쓰기를 거부한다.
 * 10장 재고 서비스는 예약 상태 전이(잡음 → 확정 / 해제, 해제는 끝)로 같은 확정·해제를 한 번만 반영한다. 이 계약은 그 위에 일반 규칙
 * (명령 번호로 가리기, 예약마다 마지막 명령 번호보다 작은 명령 거르기)이 더 필요한지도 같은 시험으로 본다.
 * 예약은 결제 서버를 거치지 않고 재고 서비스에 HTTP 로 직접 잡는다(예약 번호와 상품 번호는 결제 서버가 쓰는 번호와 겹치지 않게 큰 수).
 * 명령은 시험이 재고 명령 토픽에 직접 싣는다(키 = 예약 번호 = 결제 번호, 결제 서버 릴레이와 같다). 명령 번호는 큰 수에서 시작한다.
 * 재고 서비스의 다시 시도 간격·일시 장애 길이는 주문 서비스 계약과 같다(2·4·4·4초, 장애 6초).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "test.inventory.properties=inventory.retry.initial-interval=2s;inventory.retry.multiplier=2;"
                        + "inventory.retry.max-interval=4s;inventory.retry.max-retries=4"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InventoryServerConfiguration.class})
abstract class StockCommandConsumerContract {

    static final int STOCK = 5;
    static final int QUANTITY = 2;
    static final Duration FIRST_RETRY = Duration.ofSeconds(2);
    static final Duration OUTAGE = Duration.ofSeconds(6);
    static final Duration ARRIVAL = Duration.ofSeconds(30);
    private static final AtomicLong NUMBERS = new AtomicLong(1_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000L) * 1000);

    @Autowired
    InventoryServer inventory;

    @Autowired
    MySQLContainer mysqlContainer;

    MessageFeed feed;

    ReceiverOutage outage;

    @BeforeEach
    void openFeed() {
        feed = MessageFeed.open();
        outage = ReceiverOutage.of(mysqlContainer);
    }

    @AfterEach
    void closeFeed() {
        outage.end();
        inventory.reset();
        feed.close();
    }

    // 실패 모음에 지금까지 남은 명령. 운영 구현은 <토픽>.DLT 이고, 다른 곳에 모으는 비교 구현은 바꾼다
    List<DeadLetter> deadLetters() {
        return DeadLetterTopic.read(DeadLetterTopic.of(inventory.commandsTopic()));
    }

    void replayDeadLetters() {
        inventory.replayDeadLetters();
    }

    @Test
    @DisplayName("같은 확정을 다시 읽어도 예약이 잡은 재고는 한 번만 줄어 있다")
    void rereadConfirmKeepsStockOnce() {
        Reservation reservation = reserved();
        Command confirm = Command.of(reservation, "CONFIRM");

        send(confirm);
        awaitStatus(reservation, "CONFIRMED");
        send(confirm);
        awaitCaughtUp();

        assertThat(inventory.stockOf(reservation.productId)).isEqualTo(STOCK - QUANTITY);
    }

    @Test
    @DisplayName("같은 해제를 다시 읽어도 재고는 한 번만 돌아온다")
    void rereadReleaseReturnsStockOnce() {
        Reservation reservation = reserved();
        Command release = Command.of(reservation, "RELEASE");

        send(release);
        awaitStatus(reservation, "RELEASED");
        send(release);
        awaitCaughtUp();

        assertThat(inventory.stockOf(reservation.productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("해제 뒤에 그보다 먼저 만든 확정이 늦게 와도 예약은 해제 상태로 남는다")
    void lateOlderConfirmDoesNotRevertReleasedReservation() {
        Reservation reservation = reserved();
        Command confirm = Command.of(reservation, "CONFIRM");
        Command release = confirm.next("RELEASE");

        send(release);
        awaitStatus(reservation, "RELEASED");
        send(confirm);
        awaitCaughtUp();

        assertThat(inventory.reservationStatusOf(reservation.id)).isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("모르는 재고 명령은 예약을 바꾸지 않는다")
    void unknownCommandDoesNotChangeReservation() {
        Reservation reservation = reserved();

        send(Command.of(reservation, "HOLD"));
        awaitCaughtUp();

        assertThat(inventory.reservationStatusOf(reservation.id)).isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("모르는 재고 명령은 실패 모음에 남는다")
    void unknownCommandGoesToDeadLetters() {
        Command unknown = Command.of(reserved(), "HOLD");

        send(unknown);

        awaitDeadLetter(unknown.body());
    }

    @Test
    @DisplayName("읽을 수 없는 재고 명령은 실패 모음에 남는다")
    void unreadableCommandGoesToDeadLetters() {
        String body = "{\"commandId\": \"" + NUMBERS.incrementAndGet() + "\", \"reservationId\": ";

        feed.send(inventory.commandsTopic(), "broken-" + UUID.randomUUID(), body);

        awaitDeadLetter(body);
    }

    @Test
    @DisplayName("처리할 수 없는 명령이 있어도 같은 자리의 뒤 명령은 첫 다시 시도 간격 안에 반영된다")
    void unprocessableCommandDoesNotHoldNextCommand() {
        Reservation first = reserved();
        Reservation next = reservedOnSamePartitionAs(first);

        long sentAt = System.nanoTime();
        send(Command.of(first, "HOLD"));
        send(Command.of(next, "CONFIRM"));
        awaitStatus(next, "CONFIRMED");

        assertThat(Duration.ofNanos(System.nanoTime() - sentAt)).isLessThan(FIRST_RETRY);
    }

    @Test
    @DisplayName("재고 서비스의 저장소가 잠시 쓰기를 거부한 동안 온 명령도 저장소가 돌아오면 반영된다")
    void commandDuringOutageIsAppliedAfterRecovery() {
        Reservation reservation = reserved();
        outage.begin();

        send(Command.of(reservation, "CONFIRM"));
        PaymentEventConsumerContract.sleep(OUTAGE);
        outage.end();

        awaitStatus(reservation, "CONFIRMED");
    }

    @Test
    @DisplayName("다시 시도를 다 쓴 명령은 실패 모음에 그 원인과 함께 남는다")
    void exhaustedCommandGoesToDeadLettersWithCause() {
        Command confirm = Command.of(reserved(), "CONFIRM");
        outage.begin();

        send(confirm);
        DeadLetter letter = awaitDeadLetter(confirm.body());

        assertThat(letter.cause()).isNotBlank();
    }

    @Test
    @DisplayName("실패 모음에서 다시 넣은 명령은 아직 반영되지 않았으면 반영된다")
    void replayedCommandIsAppliedWhenNotYetApplied() {
        Reservation reservation = reserved();
        Command confirm = Command.of(reservation, "CONFIRM");
        outage.begin();
        send(confirm);
        awaitDeadLetter(confirm.body());
        outage.end();

        replayDeadLetters();

        awaitStatus(reservation, "CONFIRMED");
    }

    @Test
    @DisplayName("실패 모음에서 다시 넣은 명령은 그사이 더 새 명령이 반영됐으면 무시된다")
    void replayedCommandIsIgnoredAfterNewerCommand() {
        Reservation reservation = reserved();
        Command confirm = Command.of(reservation, "CONFIRM");
        outage.begin();
        send(confirm);
        awaitDeadLetter(confirm.body());
        outage.end();
        send(confirm.next("RELEASE"));
        awaitStatus(reservation, "RELEASED");

        replayDeadLetters();
        awaitCaughtUp();

        assertThat(inventory.reservationStatusOf(reservation.id)).isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("같은 예약의 확정을 다시 시도하는 동안 해제가 와도 예약은 해제로 끝나고 재고는 돌아온다")
    void releaseWhileConfirmIsRetriedReturnsStock() {
        Reservation reservation = reserved();
        Command confirm = Command.of(reservation, "CONFIRM");
        inventory.failReservationChange(reservation.id, "CONFIRMED");

        send(confirm);
        send(confirm.next("RELEASE"));
        PaymentEventConsumerContract.sleep(OUTAGE);
        inventory.restoreReservationChange();
        awaitCaughtUp();

        assertThat(inventory.stockOf(reservation.productId)).isEqualTo(STOCK);
    }

    Reservation reserved() {
        Reservation reservation = Reservation.of(NUMBERS.incrementAndGet(), NUMBERS.incrementAndGet());
        inventory.stock(reservation.productId, STOCK);
        inventory.reserve(reservation.id, reservation.productId, QUANTITY);
        return reservation;
    }

    Reservation reservedOnSamePartitionAs(Reservation other) {
        int partition = MessageFeed.partitionOf(String.valueOf(other.id), KafkaBroker.PARTITIONS);
        Reservation candidate = reserved();
        while (MessageFeed.partitionOf(String.valueOf(candidate.id), KafkaBroker.PARTITIONS) != partition) {
            candidate = reserved();
        }
        return candidate;
    }

    void send(Command command) {
        feed.send(inventory.commandsTopic(), String.valueOf(command.reservation.id), command.body());
    }

    void awaitCaughtUp() {
        inventory.awaitCaughtUp();
    }

    void awaitStatus(Reservation reservation, String status) {
        await().atMost(ARRIVAL).pollInterval(Duration.ofMillis(20))
                .until(() -> status.equals(inventory.reservationStatusOf(reservation.id)));
    }

    DeadLetter awaitDeadLetter(String body) {
        return await().atMost(ARRIVAL).pollInterval(Duration.ofMillis(200))
                .until(() -> deadLetters().stream().filter(letter -> body.equals(letter.value())).findFirst(), Optional::isPresent)
                .orElseThrow();
    }

    /** 시험이 잡은 예약 하나(예약 번호 = 메시지 키)와 그 상품 */
    static class Reservation {

        private final long id;
        private final long productId;

        private Reservation(long id, long productId) {
            this.id = id;
            this.productId = productId;
        }

        static Reservation of(long id, long productId) {
            return new Reservation(id, productId);
        }
    }

    /** 시험이 싣는 재고 명령 한 건(결제 서버가 보내는 본문과 같은 모양) */
    static class Command {

        private final Reservation reservation;
        private final long commandId;
        private final String command;

        private Command(Reservation reservation, long commandId, String command) {
            this.reservation = reservation;
            this.commandId = commandId;
            this.command = command;
        }

        static Command of(Reservation reservation, String command) {
            return new Command(reservation, NUMBERS.incrementAndGet(), command);
        }

        // 같은 예약의 다음 명령(결제 서버가 뒤에 남겨 번호가 더 크다)
        Command next(String nextCommand) {
            return new Command(reservation, NUMBERS.incrementAndGet(), nextCommand);
        }

        String body() {
            return "{\"commandId\":\"" + commandId + "\",\"reservationId\":" + reservation.id + ",\"orderId\":\"order-"
                    + reservation.id + "\",\"command\":\"" + command + "\"}";
        }
    }
}
