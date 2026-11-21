package com.example.payment;

import static org.awaitility.Awaitility.await;

import com.example.order.OrderServiceApplication;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
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
 * 11장: 결제 이벤트 토픽도 띄울 때마다 새 이름으로 만든다. 이벤트 고유 번호(결제 서버의 payment_outbox 번호)는 컨텍스트(MySQL 컨테이너)마다
 * 1부터 다시 매겨져, 같은 토픽을 쓰면 다른 컨텍스트의 이벤트가 같은 번호로 섞여 들어온다(10장 재고 명령 토픽과 같은 이유).
 * 시험이 주문 서비스에 비교 구현을 끼울 때는 components(빈으로 더할 클래스)와 properties 를 넘긴다(10장 InventoryServer 와 같은 방식).
 */
public class OrderServer {

    public static final String SCHEMA = "order_service";
    private static final String USER = "order_service";

    private final ConfigurableApplicationContext context;
    private final JdbcTemplate orders;
    private final String topic;
    private final String groupId;
    private final List<Class<?>> components;
    private final List<String> properties;

    private OrderServer(ConfigurableApplicationContext context, JdbcTemplate orders, String topic, String groupId,
                        List<Class<?>> components, List<String> properties) {
        this.context = context;
        this.orders = orders;
        this.topic = topic;
        this.groupId = groupId;
        this.components = components;
        this.properties = properties;
    }

    // 11장: topic 은 결제 서버가 결제 이벤트를 보내는 토픽(이 컨텍스트만의 이름). components·extraProperties 는 비교 구현을 끼울 때 넘긴다
    public static OrderServer start(MySQLContainer mysql, String topic, List<Class<?>> components, List<String> extraProperties) {
        createSchema(mysql);
        createTopic(topic);
        String url = mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/" + SCHEMA);
        String groupId = "order-service-" + UUID.randomUUID();
        List<String> properties = new ArrayList<>(List.of(
                "spring.config.name=order-service",
                "spring.datasource.url=" + url,
                "spring.datasource.username=" + USER,
                "spring.datasource.password=" + USER,
                "spring.kafka.consumer.group-id=" + groupId,
                "order.payment-events.topic=" + topic));
        properties.addAll(KafkaBroker.clientProperties());
        properties.addAll(extraProperties);
        ConfigurableApplicationContext context = run(components, properties);
        return new OrderServer(context, new JdbcTemplate(new DriverManagerDataSource(url, USER, USER)), topic, groupId,
                List.copyOf(components), List.copyOf(properties));
    }

    private static ConfigurableApplicationContext run(List<Class<?>> components, List<String> properties) {
        return new SpringApplicationBuilder(OrderServiceApplication.class)
                .sources(components.toArray(Class<?>[]::new))
                .run(properties.stream().map(property -> "--" + property).toArray(String[]::new));
    }

    public static String newTopic() {
        return KafkaBroker.TOPIC + "-" + UUID.randomUUID();
    }

    private static void createTopic(String topic) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaBroker.bootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, KafkaBroker.PARTITIONS, (short) 1))).all().get();
        } catch (ExecutionException | InterruptedException failed) {
            throw new IllegalStateException("Could not create the payment event topic.", failed);
        }
    }

    // 11장: 결제 서버가 결제 이벤트를 보내는 토픽(주문 서비스가 읽는 토픽)
    public String topic() {
        return topic;
    }

    // 11장: 결제 서버가 이 주문에 대해 토픽에 실은 이벤트의 고유 번호를 실린 순서대로(주문 서비스가 받아 남긴 것이 아니라 토픽 자체 — 11장 결정 1부터
    // 주문 서비스는 같은 번호를 한 번만 남겨, 결제 서버가 같은 이벤트를 다시 보냈는지는 토픽에서 본다)
    public List<String> sentEventIds(String orderId) {
        String quoted = "\"orderId\":\"" + orderId + "\"";
        return DeadLetterTopic.records(topic).stream()
                .filter(record -> Objects.nonNull(record.value()) && record.value().contains(quoted))
                .map(record -> eventIdOf(record.value()))
                .toList();
    }

    private static String eventIdOf(String body) {
        int start = body.indexOf("\"eventId\":\"") + "\"eventId\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    // 11장: 주문 서비스가 결제 이벤트 토픽을 다 읽어 처리할 때까지 기다린다(소비자 그룹이 커밋한 자리가 토픽 끝에 닿을 때까지)
    public void awaitCaughtUp(Duration atMost) {
        TopicLag.awaitCaughtUp(groupId, List.of(topic), atMost);
    }

    // 11장: 운영자가 실패 모음(<토픽>.DLT)의 메시지를 원래 토픽으로 다시 넣는 명령을 돌린다 — 같은 설정(같은 소비자 그룹 이름)으로 주문 서비스를
    // 다시 넣기 모드(order.dead-letter.replay=true, 리스너는 띄우지 않음)로 한 번 띄우고, 명령이 끝나면 닫는다
    public void replayDeadLetters() {
        List<String> replay = new ArrayList<>(properties);
        replay.add("order.dead-letter.replay=true");
        replay.add("spring.kafka.listener.auto-startup=false");
        ConfigurableApplicationContext command = run(components, replay);
        if (command.isActive()) {
            command.close();
        }
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

    // 11장: 주문을 마지막으로 바꾼 때. 주문이 아직 없으면 비어 있다
    public Instant updatedAtOf(String orderId) {
        return orders.query("select updated_at from orders where order_id = ?",
                (row, number) -> row.getTimestamp("updated_at").toInstant(), orderId).stream()
                .findFirst()
                .orElse(null);
    }

    // 11장: 이 주문에 대해 받은 결제 결과의 수(같은 이벤트가 다시 와 남은 줄도 센다)
    public int receivedCount(String orderId) {
        Integer count = orders.queryForObject("select count(*) from received_payment_event where order_id = ?", Integer.class,
                orderId);
        return Objects.requireNonNullElse(count, 0);
    }

    // 11장: 이 주문을 이 결제 결과로 남기거나 바꾸는 기록이 실패하게 한다(받는 쪽 DB 가 이 주문의 이 변경만 일시적으로 쓰지 못함)
    public void failOrderWrites(String orderId, String paymentStatus) {
        orders.execute("create trigger fail_order_insert before insert on orders for each row\n"
                + "begin\n"
                + "    if new.order_id = '" + orderId + "' and new.payment_status = '" + paymentStatus + "' then\n"
                + "        signal sqlstate '45000' set message_text = 'order write failed for test';\n"
                + "    end if;\n"
                + "end");
        orders.execute("create trigger fail_order_update before update on orders for each row\n"
                + "begin\n"
                + "    if new.order_id = '" + orderId + "' and new.payment_status = '" + paymentStatus + "' then\n"
                + "        signal sqlstate '45000' set message_text = 'order write failed for test';\n"
                + "    end if;\n"
                + "end");
    }

    public void restoreOrderWrites() {
        orders.execute("drop trigger if exists fail_order_insert");
        orders.execute("drop trigger if exists fail_order_update");
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
