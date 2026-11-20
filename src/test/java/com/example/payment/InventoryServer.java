package com.example.payment;

import static org.awaitility.Awaitility.await;

import com.example.inventory.InventoryServiceApplication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 10장: 시험 JVM 안에 재고 서비스를 띄운다(9장 OrderServer 와 같은 방식). 결제 서버와 같은 MySQL 컨테이너에 재고 서비스의 스키마
 * (inventory_service)와 계정을 따로 만들어 그 계정으로만 쓴다. 결제 서버는 중계(InventoryLink)를 거쳐 재고 서비스를 부른다.
 * 재고 명령 토픽은 띄울 때마다 새 이름으로 만든다 — 같은 브로커를 쓰는 다른 시험 컨텍스트의 결제 서버가 보낸 명령(결제 번호가 겹친다)을
 * 이 재고 서비스가 받지 않게 한다. 소비자 그룹도 띄울 때마다 새로 정한다.
 * 시험이 재고 서비스에 비교 구현을 끼울 때는 components(빈으로 더할 클래스)와 properties 를 넘긴다.
 */
public class InventoryServer {

    public static final String SCHEMA = "inventory_service";
    private static final String USER = "inventory_service";
    private static final int PARTITIONS = 2;

    private final ConfigurableApplicationContext context;
    private final JdbcTemplate inventory;
    private final InventoryLink link;
    private final Admin admin;
    private final String groupId;
    private final List<String> topics;

    private InventoryServer(ConfigurableApplicationContext context, JdbcTemplate inventory, InventoryLink link, Admin admin,
                            String groupId, List<String> topics) {
        this.context = context;
        this.inventory = inventory;
        this.link = link;
        this.admin = admin;
        this.groupId = groupId;
        this.topics = topics;
    }

    // commandsTopic: 결제 서버가 재고 명령을 보내는 토픽. eventsTopic: 재고 서비스가 결제 이벤트를 읽는 비교 구현이 쓰는 토픽(없으면 비어 있다)
    public static InventoryServer start(MySQLContainer mysql, String commandsTopic, String eventsTopic,
                                        List<Class<?>> components, List<String> extraProperties) {
        createSchema(mysql);
        Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaBroker.bootstrapServers()));
        // 10장 결정 1 비교(승인 먼저)는 재고 서비스가 답을 이 토픽 뒤에 "-replies" 를 붙인 토픽으로 돌려준다
        List<String> topics = new ArrayList<>(List.of(commandsTopic, commandsTopic + "-replies"));
        if (Objects.nonNull(eventsTopic)) {
            topics.add(eventsTopic);
        }
        createTopics(admin, topics);
        String url = mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/" + SCHEMA);
        String groupId = "inventory-service-" + UUID.randomUUID();
        List<String> properties = new ArrayList<>(List.of(
                "spring.config.name=inventory-service",
                "server.port=0",
                "spring.datasource.url=" + url,
                "spring.datasource.username=" + USER,
                "spring.datasource.password=" + USER,
                "spring.kafka.consumer.group-id=" + groupId,
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "inventory.stock-commands.topic=" + commandsTopic));
        if (Objects.nonNull(eventsTopic)) {
            properties.add("inventory.payment-events.topic=" + eventsTopic);
        }
        properties.addAll(KafkaBroker.clientProperties());
        properties.addAll(extraProperties);
        ConfigurableApplicationContext context = new SpringApplicationBuilder(InventoryServiceApplication.class)
                .sources(components.toArray(Class<?>[]::new))
                .run(properties.stream().map(property -> "--" + property).toArray(String[]::new));
        int port = Integer.parseInt(Objects.requireNonNull(context.getEnvironment().getProperty("local.server.port")));
        return new InventoryServer(context, new JdbcTemplate(new DriverManagerDataSource(url, USER, USER)),
                InventoryLink.open(port), admin, groupId, topics);
    }

    // 결제 서버 계정(test)은 다른 스키마를 만들 수 없어 MySQL 관리자 계정으로 만든다
    private static void createSchema(MySQLContainer mysql) {
        String serverUrl = mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/");
        JdbcTemplate root = new JdbcTemplate(new DriverManagerDataSource(serverUrl, "root", mysql.getPassword()));
        root.execute("create database if not exists " + SCHEMA);
        root.execute("create user if not exists '" + USER + "'@'%' identified by '" + USER + "'");
        root.execute("grant all on " + SCHEMA + ".* to '" + USER + "'@'%'");
    }

    private static void createTopics(Admin admin, List<String> topics) {
        try {
            admin.createTopics(topics.stream().map(topic -> new NewTopic(topic, PARTITIONS, (short) 1)).toList()).all().get();
        } catch (ExecutionException | InterruptedException failed) {
            throw new IllegalStateException("Could not create the inventory topics.", failed);
        }
    }

    public static String newCommandsTopic() {
        return "stock-commands-" + UUID.randomUUID();
    }

    // 결제 서버가 부를 재고 서비스 주소(중계)
    public String url() {
        return link.url();
    }

    public String commandsTopic() {
        return topics.get(0);
    }

    // 재고 서비스가 결제 이벤트를 읽는 비교 구현의 결제 이벤트 토픽. 그 비교가 아니면 비어 있다
    public Optional<String> eventsTopic() {
        return topics.stream().skip(2).findFirst();
    }

    public void stock(Long productId, int quantity) {
        inventory.update("insert into stock (product_id, quantity) values (?, ?)", productId, quantity);
    }

    public int stockOf(Long productId) {
        return Objects.requireNonNull(inventory.queryForObject("select quantity from stock where product_id = ?",
                Integer.class, productId));
    }

    // 확정된 예약이 이 상품을 잡은 수량의 합(10장 결정 3부터의 예약 기록)
    public int confirmedQuantityOf(Long productId) {
        Integer confirmed = inventory.queryForObject("select coalesce(sum(i.quantity), 0) from stock_reservation_item i "
                + "join stock_reservation r on r.reservation_id = i.reservation_id "
                + "where r.status = 'CONFIRMED' and i.product_id = ?", Integer.class, productId);
        return Objects.requireNonNullElse(confirmed, 0);
    }

    // 결제 서버가 보낸 재고 명령을 재고 서비스가 다 읽어 처리할 때까지 기다린다(읽은 자리가 토픽 끝에 닿을 때까지)
    public void awaitCaughtUp() {
        List<String> read = topics.stream().filter(Predicate.not(InventoryServer::isReplies)).toList();
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(() -> isCaughtUp(groupId, read));
    }

    private static boolean isReplies(String topic) {
        return topic.endsWith("-replies");
    }

    private boolean isCaughtUp(String group, List<String> read) throws ExecutionException, InterruptedException {
        Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(group)
                .partitionsToOffsetAndMetadata().get();
        Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
        read.forEach(topic -> IntStream.range(0, PARTITIONS)
                .forEach(partition -> latest.put(new TopicPartition(topic, partition), OffsetSpec.latest())));
        ListOffsetsResult ends = admin.listOffsets(latest);
        for (TopicPartition partition : latest.keySet()) {
            long end = ends.partitionResult(partition).get().offset();
            if (end > 0 && isBehind(committed.get(partition), end)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBehind(OffsetAndMetadata committed, long end) {
        return Objects.isNull(committed) || committed.offset() < end;
    }

    // 재고 서비스가 멈춘다: HTTP 로 닿지 않고, 재고 명령도 읽지 않는다
    public void down() {
        link.cut();
        listeners().ifPresent(registry -> registry.getListenerContainers().forEach(MessageListenerContainer::stop));
    }

    // 멈췄던 재고 서비스가 돌아온다. 읽던 자리부터 다시 읽는다
    public void up() {
        link.restore();
        listeners().ifPresent(registry -> registry.getListenerContainers().forEach(MessageListenerContainer::start));
    }

    private Optional<KafkaListenerEndpointRegistry> listeners() {
        return context.getBeanProvider(KafkaListenerEndpointRegistry.class).stream().findFirst();
    }

    public InventoryLink link() {
        return link;
    }

    // 재고를 줄이는 기록을 1초 붙잡는다(8장 SlowWrites 와 같은 방법). 재고 줄의 잠금을 쥔 채 잠든다
    public void holdStockDecrease() {
        inventory.execute("create trigger hold_stock_decrease before update on stock for each row\n"
                + "begin\n"
                + "    if new.quantity < old.quantity then\n"
                + "        do sleep(" + SlowWrites.HOLD.toSeconds() + ");\n"
                + "    end if;\n"
                + "end");
    }

    // 붙잡힌 기록이 잠들 때까지 기다린다(재고 서비스 계정의 연결만 보인다)
    public void awaitHeld() {
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(10)).until(() -> {
            Integer sleeping = inventory.queryForObject(
                    "select count(*) from information_schema.processlist where state = 'User sleep'", Integer.class);
            return Objects.requireNonNullElse(sleeping, 0) > 0;
        });
    }

    // 붙잡은 재고 줄이기를 풀고, 잠들어 있던 기록이 끝날 때까지 기다린다
    public void releaseHold() {
        inventory.execute("drop trigger if exists hold_stock_decrease");
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(10)).until(() -> {
            Integer sleeping = inventory.queryForObject(
                    "select count(*) from information_schema.processlist where state = 'User sleep'", Integer.class);
            return Objects.requireNonNullElse(sleeping, 0) == 0;
        });
    }

    // 시험 하나가 끝나면 붙잡기·끊기·늦추기를 모두 되돌린다
    public void reset() {
        inventory.execute("drop trigger if exists hold_stock_decrease");
        link.restore();
        link.awaitDelivered();
        listeners().ifPresent(registry -> registry.getListenerContainers().stream()
                .filter(Predicate.not(MessageListenerContainer::isRunning))
                .forEach(MessageListenerContainer::start));
    }

    public void stop() {
        link.close();
        admin.close();
        context.close();
    }
}
