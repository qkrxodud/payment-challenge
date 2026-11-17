package com.example.payment;

import com.example.mockpg.MerchantConsole;
import com.example.mockpg.MockPgApplication;
import com.example.mockpg.Outcome;
import com.example.mockpg.PaymentRecord;
import com.example.mockpg.Scenario;
import com.example.mockpg.ScenarioStore;
import com.example.mockpg.WebhookDelivery;
import com.example.mockpg.WebhookSender;
import com.example.mockpg.WebhookSettings;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
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
    // 6장: 시험에서 쓰는 웹훅 재전송 간격. 토스페이먼츠는 1, 4, 16, 64, 256, 1024, 4096분이다(횟수 7회는 같게 둔다)
    public static final List<Duration> TEST_RETRY_INTERVALS = Collections.nCopies(7, Duration.ofMillis(100));

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

    // 5장부터: 승인은 바로 하고, 그 뒤의 조회·취소에는 delay 만큼 늦게 답한다. 취소는 기다린 뒤에 처리된다
    public void answerFollowUpsAfter(Duration delay) {
        scenarios().change(Scenario.of(Duration.ZERO, Outcome.APPROVE, delay));
    }

    // 5장부터: 조회·취소에 아무것도 처리하지 않고 서버 오류(500)로 답한다
    public void failFollowUps() {
        scenarios().change(Scenario.failingFollowUps());
    }

    // 5장부터: 결제 서버를 거치지 않고 카드사 쪽에서 남은 금액을 모두 취소한다(가맹점 관리 화면에서 취소한 경우).
    // 6장부터 웹훅을 켜 두었으면 알림도 간다
    public void cancelAtCardCompany(String orderId) {
        cancelAtCardCompany(orderId, null);
    }

    // 6장: 카드사 쪽에서 이 금액만 취소한다
    public void cancelAtCardCompany(String orderId, BigDecimal amount) {
        context.getBean(MerchantConsole.class).cancel(orderId, amount, "Canceled on the merchant console");
    }

    // 6장: 결제 상태가 바뀔 때 이 주소로 웹훅을 보낸다. 재전송은 7회, 간격은 시험용으로 줄인 값
    public void sendWebhooksTo(String targetUrl) {
        webhooks().change(WebhookSettings.sendTo(targetUrl, TEST_RETRY_INTERVALS));
    }

    // 6장: 알림을 보내지 않고 쌓아 둔다
    public void holdWebhooks() {
        webhooks().change(webhooks().current().holding(true));
    }

    // 6장: 쌓아 둔 알림을 보낸다. reversed 면 늦게 생긴 알림부터. 그 뒤의 알림은 바로 보낸다
    public void releaseWebhooks(boolean reversed) {
        webhooks().change(webhooks().current().holding(false));
        webhooks().releaseHeld(reversed);
    }

    // 6장: 알림마다 같은 본문을 두 번 보낸다
    public void duplicateWebhooks() {
        webhooks().change(webhooks().current().duplicating(true));
    }

    public void stopDuplicatingWebhooks() {
        webhooks().change(webhooks().current().duplicating(false));
    }

    // 6장: 상태가 바뀌고 이만큼 지난 뒤에 알림을 보낸다
    public void delayWebhooks(Duration delay) {
        webhooks().change(webhooks().current().delayedBy(delay));
    }

    // 6장: 지금 상태를 새 알림으로 다시 보낸다(같은 상태, 다른 createdAt)
    public void resendWebhook(String orderId) {
        scenarios().findByOrderId(orderId).ifPresent(webhooks()::statusChanged);
    }

    // 6장: 카드사 기록과 다른 상태를 담은 위조 알림을 보낸다
    public void forgeWebhook(String orderId, String status, BigDecimal amount) {
        webhooks().forge(orderId, status, amount);
    }

    // 6장: 보낸 알림이 모두 200 을 받았거나 재전송을 다 썼다
    public boolean isWebhookSettled() {
        return webhooks().isSettled();
    }

    public List<WebhookDelivery> webhookDeliveries(String orderId) {
        return webhooks().deliveriesOf(orderId);
    }

    public List<WebhookDelivery> webhookDeliveries() {
        return webhooks().deliveries();
    }

    public void resetWebhooks() {
        webhooks().reset();
    }

    // 6장: 승인은 기록하고 알리되, 승인 답은 releaseConfirmAnswers 까지 보내지 않는다
    public void holdConfirmAnswers() {
        scenarios().holdConfirmAnswers();
    }

    public void releaseConfirmAnswers() {
        scenarios().releaseConfirmAnswers();
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

    // 실제로 취소한 횟수(부분 취소 포함)
    public int cancelCount(String orderId) {
        return scenarios().cancelCount(orderId);
    }

    // 5장부터: 결과와 상관없이 취소 요청을 받은 횟수(같은 멱등키로 다시 와 처음 응답을 돌려준 요청도 센다)
    public int cancelRequestCount(String orderId) {
        return scenarios().cancelRequestCount(orderId);
    }

    // 7장부터: 결과와 상관없이 주문번호 결제 조회를 받은 횟수(4장 보정, 5장 취소 보정, 6장 웹훅 처리가 모두 이 조회를 쓴다)
    public int inquiryCount(String orderId) {
        return scenarios().inquiryCount(orderId);
    }

    /** 5장부터: 카드사에 남은 이 주문의 취소할 수 있는 금액(잔액). 승인 요청이 닿지 않았으면 비어 있다 */
    public BigDecimal balanceAmount(String orderId) {
        return scenarios().findByOrderId(orderId)
                .map(PaymentRecord::getBalanceAmount)
                .orElse(null);
    }

    /** 카드사에 남은 이 주문의 결제 상태(DONE, ABORTED, PARTIAL_CANCELED, CANCELED). 승인 요청이 닿지 않았으면 NONE */
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

    private WebhookSender webhooks() {
        return context.getBean(WebhookSender.class);
    }
}
