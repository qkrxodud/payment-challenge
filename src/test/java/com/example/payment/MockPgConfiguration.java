package com.example.payment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class MockPgConfiguration {

    @Bean(destroyMethod = "stop")
    MockPgServer mockPgServer() {
        return MockPgServer.start();
    }

    @Bean
    DynamicPropertyRegistrar mockPgUrl(MockPgServer mockPgServer) {
        return registry -> registry.add("payment.pg.base-url", mockPgServer::baseUrl);
    }
}
