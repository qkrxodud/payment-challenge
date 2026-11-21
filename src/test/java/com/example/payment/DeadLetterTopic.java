package com.example.payment;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * 11장: 실패 모음 토픽(<토픽>.DLT)에 지금까지 남은 메시지를 처음부터 끝까지 읽는다(소비자 그룹 없이 — 받는 쪽의 읽은 자리를 건드리지 않는다).
 * 토픽이 아직 없으면(실패 모음을 두지 않은 구현) 비어 있다. 다른 토픽(결제 이벤트 토픽 등)에 실린 것을 그대로 읽을 때도 쓴다(records).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DeadLetterTopic {

    public static String of(String topic) {
        return topic + ".DLT";
    }

    public static List<DeadLetter> read(String deadLetterTopic) {
        return records(deadLetterTopic).stream()
                .map(DeadLetterTopic::toLetter)
                .toList();
    }

    // 11장: 이 토픽에 지금까지 실린 메시지를 처음부터 끝까지(파티션마다 실린 순서). 토픽이 없으면 비어 있다
    public static List<ConsumerRecord<String, String>> records(String topic) {
        List<TopicPartition> partitions = partitionsOf(topic);
        if (partitions.isEmpty()) {
            return List.of();
        }
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaBroker.bootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false))) {
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            while (isBehind(consumer, ends)) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records;
        }
    }

    private static List<TopicPartition> partitionsOf(String topic) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaBroker.bootstrapServers()))) {
            if (isMissing(admin, topic)) {
                return List.of();
            }
            TopicDescription description = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
            return description.partitions().stream()
                    .map(partition -> new TopicPartition(topic, partition.partition()))
                    .toList();
        } catch (ExecutionException | InterruptedException failed) {
            throw new IllegalStateException("Could not read the dead letter topic.", failed);
        }
    }

    private static boolean isMissing(Admin admin, String topic) throws ExecutionException, InterruptedException {
        return admin.listTopics().names().get().stream().noneMatch(topic::equals);
    }

    private static boolean isBehind(KafkaConsumer<String, String> consumer, Map<TopicPartition, Long> ends) {
        return ends.entrySet().stream().anyMatch(end -> consumer.position(end.getKey()) < end.getValue());
    }

    private static DeadLetter toLetter(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new HashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), textOf(header));
        }
        return DeadLetter.fromHeaders(record.key(), record.value(), headers, record.timestamp());
    }

    // 원래 자리·시각 머리는 숫자(바이트)로, 예외 머리는 글자로 실린다
    private static String textOf(Header header) {
        byte[] bytes = header.value();
        if (Objects.isNull(bytes)) {
            return "";
        }
        if (header.key().equals("kafka_dlt-original-timestamp") && bytes.length == Long.BYTES) {
            return String.valueOf(ByteBuffer.wrap(bytes).getLong());
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
