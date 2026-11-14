package com.example.payment;

import com.example.mockpg.MockPgApplication;
import com.example.mockpg.Outcome;
import com.example.mockpg.Scenario;
import com.example.mockpg.ScenarioStore;
import java.time.Duration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 테스트 JVM 안에서 Mock PG 를 임의 포트로 띄운다.
 * 결제 서버와 클래스 경로를 같이 쓰므로 Mock PG 쪽에서는 DB 자동 구성을 끈다.
 */
public class MockPgServer {

    private static final String NO_DATABASE = "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
            + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration";

    private final ConfigurableApplicationContext context;

    private MockPgServer(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public static MockPgServer start() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(MockPgApplication.class)
                .properties("server.port=0", "spring.config.name=mock-pg", NO_DATABASE)
                .run();
        return new MockPgServer(context);
    }

    public String baseUrl() {
        return "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }

    public void respondAfter(Duration delay) {
        scenarios().change(Scenario.of(delay, Outcome.APPROVE));
    }

    public void decline() {
        scenarios().change(Scenario.of(Duration.ZERO, Outcome.DECLINE));
    }

    public void fail() {
        scenarios().change(Scenario.of(Duration.ZERO, Outcome.FAIL));
    }

    public void reset() {
        scenarios().reset();
    }

    public boolean isWaiting(String orderId) {
        return scenarios().isInFlight(orderId);
    }

    public int approvalCount(String orderId) {
        return scenarios().approvalCount(orderId);
    }

    public void stop() {
        context.close();
    }

    private ScenarioStore scenarios() {
        return context.getBean(ScenarioStore.class);
    }
}
