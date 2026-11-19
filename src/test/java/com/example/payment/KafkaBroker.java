package com.example.payment;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * 9장: 시험 JVM 하나에 Kafka 브로커 하나(단일 브로커, KRaft). 모든 시험 컨텍스트가 같은 브로커를 쓴다 —
 * 컨텍스트마다 MySQL 컨테이너가 하나씩 뜨는 데 더해 브로커까지 뜨지 않게 한다(8장 관찰 1, 시험 컨텍스트 캐시).
 * 클라이언트는 BrokerLink(중계)를 거쳐 브로커에 닿는다. 브로커가 클라이언트에게 알려 주는 주소도 중계의 포트다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KafkaBroker {

    public static final String IMAGE = "apache/kafka:4.2.0";
    // 결제 서버 설정(payment.events.topic, payment.events.partitions)과 같다
    public static final String TOPIC = "payment-events";
    public static final int PARTITIONS = 2;
    // 브로커가 받았다는 답을 기다리는 시간. 운영 기본값은 2분(delivery.timeout.ms)이다. 시험은 3초로 줄여, 브로커가 그보다 오래 끊기면
    // 보내기가 실패로 끝나게 한다(2분 넘게 끊긴 것과 같은 결과를 3초 만에 본다)
    public static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(3);

    private static BrokerLink link;

    public static synchronized BrokerLink link() {
        if (Objects.isNull(link)) {
            link = start();
        }
        return link;
    }

    public static String bootstrapServers() {
        return "localhost:" + link().port();
    }

    // 시험 서버가 쓰는 Kafka 설정: 중계 주소와 줄인 시간 제한(한 번의 요청 2초, 전체 3초, 토픽 정보를 기다리는 시간 3초)
    public static List<String> clientProperties() {
        return List.of(
                "spring.kafka.bootstrap-servers=" + bootstrapServers(),
                "spring.kafka.producer.properties.delivery.timeout.ms=" + DELIVERY_TIMEOUT.toMillis(),
                "spring.kafka.producer.properties.request.timeout.ms=2000",
                "spring.kafka.producer.properties.max.block.ms=" + DELIVERY_TIMEOUT.toMillis());
    }

    private static BrokerLink start() {
        int linkPort = BrokerLink.freePort();
        GenericContainer<?> broker = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withExposedPorts(9092)
                .withEnv("KAFKA_NODE_ID", "1")
                .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9094,CONTROLLER://:9093")
                .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:" + linkPort + ",BROKER://localhost:9094")
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT")
                .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER")
                .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                // 토픽은 결제 서버가 만든다(파티션 2개). 주문 서비스가 먼저 떠 구독하면 브로커가 파티션 하나짜리 토픽을 만들어 버린다
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false")
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1).withStartupTimeout(Duration.ofMinutes(2)));
        broker.start();
        BrokerLink link = BrokerLink.open(linkPort, () -> broker.getMappedPort(9092));
        createTopic(link);
        return link;
    }

    // 결제 서버가 뜰 때도 만들지만(KafkaTopicConfig), 결제 서버보다 먼저 뜨는 주문 서비스가 토픽을 기다리지 않게 브로커를 띄우며 만들어 둔다
    private static void createTopic(BrokerLink link) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + link.port()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, PARTITIONS, (short) 1))).all().get();
        } catch (ExecutionException | InterruptedException failed) {
            throw new IllegalStateException("Could not create the payment event topic.", failed);
        }
    }
}
