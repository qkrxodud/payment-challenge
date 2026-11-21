package com.example.payment;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.util.ClassUtils;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 10장: 시험 서버와 같은 MySQL 컨테이너(다른 스키마), 같은 Kafka 브로커를 쓰는 재고 서비스를 띄우고, 결제 서버가 중계를 거쳐 부르게 한다.
 * 재고 서비스에 비교 구현을 끼우는 시험은 test.inventory.components(쉼표로 나눈 클래스 이름)와 test.inventory.properties(세미콜론으로 나눈 설정),
 * 재고 서비스가 결제 이벤트를 읽는 비교 구현은 test.inventory.reads-payment-events=true 를 시험 설정에 둔다.
 * 11장: 계약이 정한 설정(test.inventory.properties)에 비교 구현이 더할 설정은 test.inventory.extra-properties 에 둔다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InventoryServerConfiguration {

    @Bean(destroyMethod = "stop")
    InventoryServer inventoryServer(MySQLContainer mysqlContainer, Environment environment) {
        String eventsTopic = environment.getProperty("test.inventory.reads-payment-events", Boolean.class, false)
                ? "payment-events-" + UUID.randomUUID()
                : null;
        return InventoryServer.start(mysqlContainer, InventoryServer.newCommandsTopic(), eventsTopic,
                classesOf(environment.getProperty("test.inventory.components", "")),
                Stream.concat(listOf(environment.getProperty("test.inventory.properties", ""), ";").stream(),
                        listOf(environment.getProperty("test.inventory.extra-properties", ""), ";").stream()).toList());
    }

    @Bean
    DynamicPropertyRegistrar inventoryUrl(InventoryServer inventoryServer) {
        return registry -> {
            registry.add("payment.inventory.base-url", inventoryServer::url);
            registry.add("payment.inventory.commands-topic", inventoryServer::commandsTopic);
            inventoryServer.eventsTopic().ifPresent(topic -> registry.add("payment.events.topic", () -> topic));
        };
    }

    private static List<Class<?>> classesOf(String names) {
        return listOf(names, ",").stream()
                .<Class<?>>map(name -> ClassUtils.resolveClassName(name, InventoryServerConfiguration.class.getClassLoader()))
                .toList();
    }

    private static List<String> listOf(String joined, String separator) {
        return Arrays.stream(joined.split(separator))
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .toList();
    }
}
