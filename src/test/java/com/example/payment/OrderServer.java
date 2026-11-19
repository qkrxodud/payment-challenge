package com.example.payment;

import static org.awaitility.Awaitility.await;

import com.example.order.OrderServiceApplication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 9장: 시험 JVM 안에 주문 서비스를 띄운다. 결제 서버와 같은 MySQL 컨테이너에 주문 서비스의 스키마(order_service)와 계정을 따로 만들어
 * 그 계정으로만 쓴다 — 결제 서버와 주문 서비스가 서로의 표를 읽지 못한다. 컨테이너를 하나 더 띄우지 않는다(8장 관찰 1).
 * 시험 컨텍스트마다 주문 서비스가 따로 뜨므로 소비자 그룹은 띄울 때마다 새로 정한다 — 같은 브로커의 같은 토픽을 읽어도
 * 다른 컨텍스트의 주문 서비스와 파티션을 나눠 갖지 않고, 토픽의 처음부터 모두 읽는다.
 */
public class OrderServer {

    public static final String SCHEMA = "order_service";
    private static final String USER = "order_service";

    private final ConfigurableApplicationContext context;
    private final JdbcTemplate orders;

    private OrderServer(ConfigurableApplicationContext context, JdbcTemplate orders) {
        this.context = context;
        this.orders = orders;
    }

    public static OrderServer start(MySQLContainer mysql) {
        createSchema(mysql);
        String url = mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/" + SCHEMA);
        List<String> properties = new ArrayList<>(List.of(
                "spring.config.name=order-service",
                "spring.datasource.url=" + url,
                "spring.datasource.username=" + USER,
                "spring.datasource.password=" + USER,
                "spring.kafka.consumer.group-id=order-service-" + UUID.randomUUID()));
        properties.addAll(KafkaBroker.clientProperties());
        ConfigurableApplicationContext context = new SpringApplicationBuilder(OrderServiceApplication.class)
                .run(properties.stream().map(property -> "--" + property).toArray(String[]::new));
        return new OrderServer(context, new JdbcTemplate(new DriverManagerDataSource(url, USER, USER)));
    }

    // 결제 서버 계정(test)은 다른 스키마를 만들 수 없어 MySQL 관리자 계정으로 만든다(Testcontainers 는 관리자 비밀번호를 시험 계정과 같게 둔다)
    private static void createSchema(MySQLContainer mysql) {
        String serverUrl = mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/");
        JdbcTemplate root = new JdbcTemplate(new DriverManagerDataSource(serverUrl, "root", mysql.getPassword()));
        root.execute("create database if not exists " + SCHEMA);
        root.execute("create user if not exists '" + USER + "'@'%' identified by '" + USER + "'");
        root.execute("grant all on " + SCHEMA + ".* to '" + USER + "'@'%'");
    }

    // 주문의 지금 상태(PAID, PAYMENT_FAILED, CANCELED). 결제 이벤트를 아직 받지 못한 주문이면 비어 있다
    public String statusOf(String orderId) {
        return orders.queryForList("select status from orders where order_id = ?", String.class, orderId).stream()
                .findFirst()
                .orElse(null);
    }

    // 이 주문에 대해 받은 결제 결과를 받은 순서대로(같은 이벤트가 다시 왔으면 다시 온 만큼)
    public List<String> receivedStatuses(String orderId) {
        return orders.queryForList("select payment_status from received_payment_event where order_id = ? order by id",
                String.class, orderId);
    }

    // 이 주문에 대해 받은 이벤트의 고유 번호를 받은 순서대로(같은 이벤트가 다시 왔으면 다시 온 만큼)
    public List<String> receivedEventIds(String orderId) {
        return orders.queryForList("select event_id from received_payment_event where order_id = ? order by id",
                String.class, orderId);
    }

    // 이 주문의 결제 결과 status 이벤트를 몇 번 받았나
    public int receivedCount(String orderId, String status) {
        Integer count = orders.queryForObject(
                "select count(*) from received_payment_event where order_id = ? and payment_status = ?", Integer.class,
                orderId, status);
        return Objects.requireNonNullElse(count, 0);
    }

    // 이 주문의 status 이벤트가 결제 서버에서 만들어진 때부터 주문 서비스가 받은 때까지(처음 받은 것)
    public Duration arrivalDelay(String orderId, String status) {
        return orders.query("select occurred_at, received_at from received_payment_event where order_id = ? and payment_status = ? "
                        + "order by id limit 1",
                (row, number) -> Duration.between(row.getTimestamp("occurred_at").toInstant(),
                        row.getTimestamp("received_at").toInstant()),
                orderId, status).stream().findFirst().orElse(null);
    }

    // 9장 측정: 이 주문에 대해 받은 이벤트를 받은 순서대로(결제 결과, 만든 때부터 받은 때까지)
    public List<Receipt> receipts(String orderId) {
        return orders.query("select payment_status, occurred_at, received_at from received_payment_event where order_id = ? order by id",
                (row, number) -> new Receipt(row.getString("payment_status"),
                        Duration.between(row.getTimestamp("occurred_at").toInstant(), row.getTimestamp("received_at").toInstant())),
                orderId);
    }

    /** 받은 이벤트 한 건: 결제 결과와 도착 지연 */
    public static class Receipt {

        private final String status;
        private final Duration delay;

        Receipt(String status, Duration delay) {
            this.status = status;
            this.delay = delay;
        }

        public String status() {
            return status;
        }

        public Duration delay() {
            return delay;
        }
    }

    public void awaitReceived(String orderId, String status, Duration atMost) {
        await().atMost(atMost).pollInterval(Duration.ofMillis(50)).until(() -> receivedCount(orderId, status) > 0);
    }

    // 결제 결과 status 이벤트를 받아 남기는 기록을 1초 붙잡는다 — 그 이벤트를 읽은 소비자가 주문을 바꾸기 전에 잠든다.
    // 같은 파티션의 다음 이벤트는 그동안 기다리고, 다른 파티션의 이벤트는 그사이에 처리된다
    public void holdReceiving(String status) {
        orders.execute("create trigger hold_received_event before insert on received_payment_event for each row\n"
                + "begin\n"
                + "    if new.payment_status = '" + status + "' then\n"
                + "        do sleep(" + SlowWrites.HOLD.toSeconds() + ");\n"
                + "    end if;\n"
                + "end");
    }

    // 붙잡힌 기록이 잠들 때까지 기다린다(주문 서비스 계정의 연결만 보인다)
    public void awaitHeld() {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10)).until(() -> {
            Integer sleeping = orders.queryForObject(
                    "select count(*) from information_schema.processlist where state = 'User sleep'", Integer.class);
            return Objects.requireNonNullElse(sleeping, 0) > 0;
        });
    }

    public void releaseHold() {
        orders.execute("drop trigger if exists hold_received_event");
    }

    public void stop() {
        context.close();
    }
}
