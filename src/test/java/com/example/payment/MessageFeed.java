package com.example.payment;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Utils;

/**
 * 11장: 받는 쪽(주문 서비스·재고 서비스)이 읽는 토픽에 시험이 메시지를 직접 싣는 도구. 같은 메시지를 다시 싣거나(다시 보냄, 다시 읽음),
 * 옛 메시지를 늦게 싣거나, 처리할 수 없는 메시지를 싣는 순간을 시험이 정한다. 메시지 키는 결제 번호 — 결제 서버의 릴레이와 같다
 * (같은 키는 같은 파티션, 파티션은 Kafka 기본 파티셔너가 키로 정한다). 브로커가 받았다고 답할 때까지 기다린다.
 */
public class MessageFeed implements AutoCloseable {

    private final KafkaProducer<String, String> producer;

    private MessageFeed(KafkaProducer<String, String> producer) {
        this.producer = producer;
    }

    public static MessageFeed open() {
        return new MessageFeed(new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaBroker.bootstrapServers(),
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
    }

    // 브로커가 받은 때(밀리초)를 돌려준다
    public long send(String topic, String key, String value) {
        try {
            RecordMetadata sent = producer.send(new ProducerRecord<>(topic, key, value)).get();
            return sent.timestamp();
        } catch (ExecutionException failed) {
            throw new IllegalStateException("Could not send a test message.", failed);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending a test message.", interrupted);
        }
    }

    // 이 키가 실릴 파티션(Kafka 기본 파티셔너가 키가 있는 메시지에 쓰는 계산과 같다)
    public static int partitionOf(String key, int partitions) {
        return Utils.toPositive(Utils.murmur2(key.getBytes(StandardCharsets.UTF_8))) % partitions;
    }

    @Override
    public void close() {
        producer.close();
    }
}
