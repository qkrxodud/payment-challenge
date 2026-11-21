package com.example.inventory.infrastructure.kafka;

import com.example.inventory.domain.deadletter.DeadLetterReplayer;
import com.example.inventory.global.config.DeadLetterProperties;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 11장 결정 4: 실패 모음 토픽을 다시 넣기 그룹으로 읽어, 명령을 시작한 때 토픽 끝까지의 명령을 원래 토픽의 같은 파티션에 같은 키·본문으로 싣는다.
 * 한 건마다 브로커가 받았다고 답한 뒤에 그 자리를 그룹에 커밋한다 — 도중에 멈추면 다음 명령이 커밋한 자리부터 다시 넣는다(다시 넣은 명령이 두 번 가도
 * 받는 쪽 결정 1이 한 번만 반영한다).
 */
@Component
@RequiredArgsConstructor
public class KafkaDeadLetterReplayer implements DeadLetterReplayer {

    private static final Duration POLL = Duration.ofMillis(500);

    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DeadLetterProperties deadLetterProperties;

    @Override
    public int replayAll() {
        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = consumerFactory.createConsumer(deadLetterProperties.getReplayGroup(), null, null,
                overrides)) {
            List<TopicPartition> partitions = consumer.partitionsFor(deadLetterProperties.getTopic()).stream()
                    .map(partition -> new TopicPartition(partition.topic(), partition.partition()))
                    .toList();
            consumer.assign(partitions);
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
            return replayUntil(consumer, ends);
        }
    }

    private int replayUntil(Consumer<String, String> consumer, Map<TopicPartition, Long> ends) {
        int replayed = 0;
        while (isBehind(consumer, ends)) {
            for (ConsumerRecord<String, String> record : consumer.poll(POLL)) {
                replayed += replayIfBefore(consumer, record, ends);
            }
        }
        return replayed;
    }

    private static boolean isBehind(Consumer<String, String> consumer, Map<TopicPartition, Long> ends) {
        return ends.entrySet().stream().anyMatch(end -> consumer.position(end.getKey()) < end.getValue());
    }

    // 명령을 시작한 뒤에 들어온 명령은 다음 명령으로 넘긴다(커밋하지 않는다)
    private int replayIfBefore(Consumer<String, String> consumer, ConsumerRecord<String, String> record,
                               Map<TopicPartition, Long> ends) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        if (record.offset() >= ends.get(partition)) {
            return 0;
        }
        kafkaTemplate.send(new ProducerRecord<>(deadLetterProperties.getSourceTopic(), record.partition(), record.key(),
                record.value())).join();
        Map<TopicPartition, OffsetAndMetadata> done = new HashMap<>();
        done.put(partition, new OffsetAndMetadata(record.offset() + 1));
        consumer.commitSync(done);
        return 1;
    }
}
