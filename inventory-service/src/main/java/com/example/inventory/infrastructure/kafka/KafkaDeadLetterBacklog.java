package com.example.inventory.infrastructure.kafka;

import com.example.inventory.domain.deadletter.DeadLetterBacklog;
import com.example.inventory.global.config.DeadLetterProperties;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 5: 실패 모음에 남은 명령 수 = 파티션마다 (토픽 끝 − 다시 넣기 그룹이 커밋한 자리). 다시 넣기 그룹이 아직 커밋하지 않은 파티션은 토픽 처음부터 센다.
 * 다시 넣기 명령은 다시 넣은 명령마다 그 자리를 이 그룹에 커밋한다(11장 결정 4) — 다시 넣은 명령은 이 수에서 빠진다.
 * 그룹에 들어가지 않고(구독하지 않고) 커밋한 자리만 읽는다. 브로커가 답하지 않으면 값이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDeadLetterBacklog implements DeadLetterBacklog {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ConsumerFactory<String, String> consumerFactory;
    private final DeadLetterProperties deadLetterProperties;

    @Override
    public OptionalLong pending() {
        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        try (Consumer<String, String> consumer = consumerFactory.createConsumer(deadLetterProperties.getReplayGroup(), null,
                "-backlog", overrides)) {
            Set<TopicPartition> partitions = Set.copyOf(consumer.partitionsFor(deadLetterProperties.getTopic(), TIMEOUT).stream()
                    .map(partition -> new TopicPartition(partition.topic(), partition.partition()))
                    .toList());
            return OptionalLong.of(pendingOf(consumer.beginningOffsets(partitions, TIMEOUT), consumer.endOffsets(partitions, TIMEOUT),
                    consumer.committed(partitions, TIMEOUT)));
        } catch (KafkaException unanswered) {
            log.warn("Could not count the dead letters ({}).", unanswered.getClass().getSimpleName());
            return OptionalLong.empty();
        }
    }

    private static long pendingOf(Map<TopicPartition, Long> beginnings, Map<TopicPartition, Long> ends,
                                  Map<TopicPartition, OffsetAndMetadata> committed) {
        return ends.entrySet().stream()
                .mapToLong(end -> end.getValue() - replayedUpTo(end.getKey(), beginnings, committed))
                .sum();
    }

    private static long replayedUpTo(TopicPartition partition, Map<TopicPartition, Long> beginnings,
                                     Map<TopicPartition, OffsetAndMetadata> committed) {
        OffsetAndMetadata replayed = committed.get(partition);
        long beginning = beginnings.get(partition);
        if (Objects.isNull(replayed)) {
            return beginning;
        }
        return Math.max(replayed.offset(), beginning);
    }
}
