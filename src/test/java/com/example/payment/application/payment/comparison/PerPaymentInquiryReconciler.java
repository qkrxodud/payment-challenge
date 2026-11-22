package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.ExclusiveJobRunner;
import com.example.payment.application.payment.PaymentWebhookRecorder;
import com.example.payment.domain.error.exception.PaymentGatewayFailedException;
import com.example.payment.domain.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.reconciliation.OurPayment;
import com.example.payment.domain.reconciliation.ReconciliationRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 12장 결정 1 비교: 결제마다 카드사 단건 조회(4장 보정을 그날 인증한 모든 결제로 넓힘). 카드사 거래 내역을 읽지 않고, 그날 인증한 우리 결제마다
 * 주문번호로 카드사 결제를 조회해 6장 따라가기로 맞춘다. 카드사에 없는 승인 결제는 우리에게만 있는 결제로 남긴다.
 * 조회 응답(Payment 객체 가운데 결제 서버가 읽는 상태·취소 이력)에는 승인 금액을 읽지 않아 금액 어긋남을 볼 수 없고, 우리 결제에서 출발하므로
 * 카드사에만 있는 결제는 찾지 못한다. 따라가기는 뒤로 가지 않으므로 우리가 앞선 결제도 찾지 못한다.
 * 맞춘 결제와 우리에게만 있는 결제는 운영 구현과 같은 대사 결과 표에 남긴다. 운영 구현과 같은 작업 잠금을 쓴다.
 */
@RequiredArgsConstructor
public class PerPaymentInquiryReconciler {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final ExclusiveJobRunner exclusiveJobRunner;
    private final ReconciliationRepository reconciliationRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentWebhookRecorder paymentWebhookRecorder;
    private final JdbcTemplate jdbcTemplate;

    public static PerPaymentInquiryReconciler on(ApplicationContext server) {
        return new PerPaymentInquiryReconciler(server.getBean(ExclusiveJobRunner.class), server.getBean(ReconciliationRepository.class),
                server.getBean(PaymentGateway.class), server.getBean(PaymentWebhookRecorder.class), server.getBean(JdbcTemplate.class));
    }

    public void reconcile(LocalDate day) {
        exclusiveJobRunner.run(ExclusiveJobRunner.DAILY_RECONCILIATION, () -> reconcileLocked(day));
    }

    private void reconcileLocked(LocalDate day) {
        Instant from = day.atStartOfDay(KOREA).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(KOREA).toInstant();
        reconciliationRepository.findAuthenticatedBetween(from, to).forEach(ours -> reconcile(day, ours));
    }

    private void reconcile(LocalDate day, OurPayment ours) {
        try {
            follow(day, ours, paymentGateway.findPayment(ours.getOrderId()));
        } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException unanswered) {
            // 다음 대사에서 다시 묻는다
        }
    }

    private void follow(LocalDate day, OurPayment ours, GatewayPayment gatewayPayment) {
        if (gatewayPayment.getStatus().isNotFound() && ours.stage().hadApproval()) {
            record(day, ours, "OURS_ONLY", "NEEDS_REVIEW");
            return;
        }
        String before = stateOf(ours.getOrderId());
        paymentWebhookRecorder.follow(ours.getOrderId(), gatewayPayment);
        if (Objects.equals(before, stateOf(ours.getOrderId()))) {
            return;
        }
        record(day, ours, "STATUS_BEHIND", "FOLLOWED");
    }

    // 결제 상태와 끝난 취소 수(부분 취소를 들여왔는지도 본다)
    private String stateOf(String orderId) {
        return jdbcTemplate.queryForObject("select concat(p.status, '/', (select count(*) from payment_cancel c "
                + "where c.payment_id = p.id and c.status = 'DONE')) from payment p where p.order_id = ?", String.class, orderId);
    }

    private void record(LocalDate day, OurPayment ours, String type, String resolution) {
        jdbcTemplate.update("insert ignore into reconciliation_result (business_date, payment_key, order_id, mismatch_type, our_status, "
                        + "our_amount, resolution, found_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                Date.valueOf(day), ours.getPaymentKey(), ours.getOrderId(), type, ours.getStatus().name(), ours.getAmount(), resolution,
                Timestamp.from(Instant.now()));
    }
}
