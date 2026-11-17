package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentCancelRecorder;
import com.example.payment.application.payment.PaymentRecorder;
import com.example.payment.application.payment.PaymentWebhookRecorder;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.config.ReconciliationProperties;
import com.example.payment.global.error.exception.PaymentGatewayFailedException;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 비교 구현(7장 결정 5): 결제마다 점유 시각을 두고 서버끼리 나눠 갖기. 주기 작업마다 대상 줄(결제, 받은 알림)에 "이 서버가 언제까지 맡는다"를
 * 조건부 UPDATE 로 먼저 적고(claimed_by, claimed_until — 맡은 시간이 지났거나 아무도 안 맡은 줄만), 자기가 맡은 줄만 처리한다.
 * 같은 줄을 두 서버가 함께 맡으려 하면 MySQL 이 한 문장씩 바꾸고 나중 문장은 이미 맡긴 줄을 건너뛴다. 처리는 운영 구현과 같은 기록을 쓴다.
 * 맡는 칸 둘은 이 시험 DB 의 payment·payment_webhook 에만 더한다. 맡은 시간은 1분이다.
 */
@RequiredArgsConstructor
public class RowClaimJobs {

    private static final Duration CLAIM = Duration.ofMinutes(1);
    private static final String APPROVAL_TARGETS = "status in ('APPROVAL_UNKNOWN', 'AUTHENTICATED') and authenticated_at < ?";

    private final JdbcTemplate jdbcTemplate;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentRecorder paymentRecorder;
    private final PaymentCancelRecorder paymentCancelRecorder;
    private final PaymentWebhookRecorder paymentWebhookRecorder;
    private final NetCancelReconciler netCancelReconciler;
    private final ReconciliationProperties reconciliationProperties;
    private final Clock clock;

    @PostConstruct
    void addClaimColumns() {
        addClaimColumns("payment");
        addClaimColumns("payment_webhook");
    }

    private void addClaimColumns(String table) {
        Integer columns = jdbcTemplate.queryForObject("select count(*) from information_schema.columns "
                + "where table_schema = database() and table_name = ? and column_name = 'claimed_until'", Integer.class, table);
        if (Objects.requireNonNullElse(columns, 0) == 0) {
            jdbcTemplate.execute("alter table " + table + " add column claimed_by varchar(100), add column claimed_until datetime(6)");
        }
    }

    // 승인 보정 뒤 취소 보정. 대상을 고르는 기준(보정 대기 시간)은 운영 구현과 같다
    public void reconcile(Instant now) {
        Instant requestedBefore = now.minus(reconciliationProperties.getWaitAfterRequest());
        claimApprovalTargets(requestedBefore).forEach(this::reconcileApproval);
        claimCancelTargets(requestedBefore).forEach(orderId -> reconcileCancels(orderId, requestedBefore));
    }

    public void netCancel(Instant now) {
        Instant requestedBefore = now.minus(reconciliationProperties.getWaitAfterRequest());
        claimApprovalTargets(requestedBefore).forEach(orderId ->
                paymentRepository.findByOrderId(orderId).ifPresent(netCancelReconciler::cancel));
    }

    public void processWebhooks() {
        String claim = newClaim();
        Instant now = clock.instant();
        jdbcTemplate.update("update payment_webhook set claimed_by = ?, claimed_until = ? where status = 'RECEIVED' "
                + "and (claimed_until is null or claimed_until <= ?) order by id limit 100",
                claim, Timestamp.from(now.plus(CLAIM)), Timestamp.from(now));
        Map<String, List<Long>> byOrder = jdbcTemplate.query("select id, order_id from payment_webhook where claimed_by = ? order by id",
                        (row, number) -> Map.entry(row.getString("order_id"), row.getLong("id")), claim).stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        byOrder.forEach(this::processWebhooks);
    }

    private List<String> claimApprovalTargets(Instant requestedBefore) {
        String claim = newClaim();
        Instant now = clock.instant();
        jdbcTemplate.update("update payment set claimed_by = ?, claimed_until = ? where " + APPROVAL_TARGETS
                + " and (claimed_until is null or claimed_until <= ?) order by authenticated_at limit 100",
                claim, Timestamp.from(now.plus(CLAIM)), Timestamp.from(requestedBefore), Timestamp.from(now));
        return claimedOrders(claim);
    }

    private List<String> claimCancelTargets(Instant requestedBefore) {
        String claim = newClaim();
        Instant now = clock.instant();
        jdbcTemplate.update("update payment p set claimed_by = ?, claimed_until = ? where exists (select 1 from payment_cancel c "
                + "where c.payment_id = p.id and c.status in ('UNKNOWN', 'REQUESTED') and c.requested_at < ?) "
                + "and (p.claimed_until is null or p.claimed_until <= ?) order by p.id limit 100",
                claim, Timestamp.from(now.plus(CLAIM)), Timestamp.from(requestedBefore), Timestamp.from(now));
        return claimedOrders(claim);
    }

    private List<String> claimedOrders(String claim) {
        return jdbcTemplate.queryForList("select order_id from payment where claimed_by = ? order by id", String.class, claim);
    }

    private void reconcileApproval(String orderId) {
        try {
            paymentRecorder.recordInquiry(orderId, paymentGateway.findPayment(orderId));
        } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException unanswered) {
            // 다음 주기에 맡은 시간이 지나면 다시 본다
        }
    }

    private void reconcileCancels(String orderId, Instant requestedBefore) {
        try {
            paymentCancelRecorder.recordInquiry(orderId, paymentGateway.findCancels(orderId), requestedBefore);
        } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException unanswered) {
            // 다음 주기에 맡은 시간이 지나면 다시 본다
        }
    }

    // 운영 처리기(PaymentWebhookProcessor)와 같은 처리: 모르는 주문은 닫고, 아는 주문은 카드사 조회 한 번으로 맞춘다
    private void processWebhooks(String orderId, List<Long> webhookIds) {
        if (paymentRepository.findByOrderId(orderId).isEmpty()) {
            paymentWebhookRecorder.ignore(webhookIds);
            return;
        }
        try {
            paymentWebhookRecorder.follow(orderId, webhookIds, paymentGateway.findPayment(orderId));
        } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException unanswered) {
            paymentWebhookRecorder.recordFailedLookup(webhookIds);
        }
    }

    private static String newClaim() {
        return "claim-" + UUID.randomUUID();
    }
}
