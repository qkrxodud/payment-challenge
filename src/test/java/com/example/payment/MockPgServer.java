package com.example.payment;

import com.example.mockpg.MockPgApplication;
import com.example.mockpg.Outcome;
import com.example.mockpg.PaymentRecord;
import com.example.mockpg.Scenario;
import com.example.mockpg.ScenarioStore;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
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

    public static final String NONE = "NONE";

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

    // 4장부터: 기다리게 한 뒤 거절한다. 결제 서버가 기다리기를 그만둬도 거절은 기록된다
    public void declineAfter(Duration delay) {
        scenarios().change(Scenario.of(delay, Outcome.DECLINE));
    }

    // 4장부터: 승인 요청이 승인 처리까지 닿지 않는다. 기다리게 한 뒤 아무것도 기록하지 않는다
    public void loseAfter(Duration delay) {
        scenarios().change(Scenario.of(delay, Outcome.LOST));
    }

    // 4장부터: 승인은 delay 뒤에 하고, 그 뒤의 조회·취소에는 followUpDelay 만큼 늦게 답한다
    public void respondAfter(Duration delay, Duration followUpDelay) {
        scenarios().change(Scenario.of(delay, Outcome.APPROVE, followUpDelay));
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

    public List<BigDecimal> approvedAmounts(String orderId) {
        return scenarios().approvedAmounts(orderId);
    }

    public int confirmRequestCount(String orderId) {
        return scenarios().confirmRequestCount(orderId);
    }

    public int cancelCount(String orderId) {
        return scenarios().cancelCount(orderId);
    }

    /** 카드사에 남은 이 주문의 결제 상태(DONE, ABORTED, CANCELED). 승인 요청이 닿지 않았으면 NONE */
    public String paymentState(String orderId) {
        return scenarios().findByOrderId(orderId)
                .map(PaymentRecord::getState)
                .map(Enum::name)
                .orElse(NONE);
    }

    public void stop() {
        context.close();
    }

    private ScenarioStore scenarios() {
        return context.getBean(ScenarioStore.class);
    }
}
