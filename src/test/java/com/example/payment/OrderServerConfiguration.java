package com.example.payment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/** 9장: 시험 서버와 같은 MySQL 컨테이너(다른 스키마), 같은 Kafka 브로커를 쓰는 주문 서비스를 띄운다 */
@TestConfiguration(proxyBeanMethods = false)
public class OrderServerConfiguration {

    @Bean(destroyMethod = "stop")
    OrderServer orderServer(MySQLContainer mysqlContainer) {
        return OrderServer.start(mysqlContainer);
    }
}
