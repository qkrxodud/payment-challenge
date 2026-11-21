package com.example.payment;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/**
 * 11장: 받는 쪽 소비자 그룹이 토픽을 다 읽어 처리했는지(커밋한 자리가 토픽 끝에 닿았는지) Kafka Admin 으로 비교한다(10장 InventoryServer 와 같은 방법).
 * Spring Kafka 리스너는 처리를 마친(또는 실패 모음으로 넘긴) 메시지의 자리만 커밋하므로, 끝에 닿았으면 그때까지 온 메시지는 모두 처리됐다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TopicLag {

    public static void awaitCaughtUp(String groupId, List<String> topics, Duration atMost) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaBroker.bootstrapServers()))) {
            await().atMost(atMost).pollInterval(Duration.ofMillis(50)).until(() -> isCaughtUp(admin, groupId, topics));
        }
    }

    private static boolean isCaughtUp(Admin admin, String groupId, List<String> topics)
            throws ExecutionException, InterruptedException {
        Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata().get();
        Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
        for (TopicDescription topic : admin.describeTopics(topics).allTopicNames().get().values()) {
            topic.partitions().forEach(partition ->
                    latest.put(new TopicPartition(topic.name(), partition.partition()), OffsetSpec.latest()));
        }
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
}
