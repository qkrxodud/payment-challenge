package com.example.payment.infrastructure.persistence.reconciliation;

import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.domain.reconciliation.Mismatch;
import com.example.payment.domain.reconciliation.OurPayment;
import com.example.payment.domain.reconciliation.ReconciliationRepository;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 12장 결정 1: 대사가 읽는 우리 결제와 대사 결과를 JDBC 로 다룬다. 결제는 엔티티로 읽지 않는다 — 대사는 하루치 결제를 모두 읽으므로
 * 결제마다 취소 목록을 따로 읽지 않고(끝난 취소의 합만) 한 문장으로 읽는다. 결제 줄을 잠그지 않는다(읽기만 한다).
 * 대사 결과는 (날짜, 결제 키, 종류) 고유 키에 insert … on duplicate key update 로 남겨, 같은 날을 다시 돌려도 한 줄이다.
 */
@Repository
@RequiredArgsConstructor
public class ReconciliationRepositoryAdapter implements ReconciliationRepository {

    // 결제 키로 찾을 때 한 문장에 넣는 키 수
    private static final int KEYS_PER_QUERY = 1000;
    private static final String OUR_PAYMENT = """
            select p.payment_key, p.order_id, p.amount, p.status,
                   coalesce((select sum(c.amount) from payment_cancel c where c.payment_id = p.id and c.status = 'DONE'), 0)
                       as canceled_amount
            from payment p
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public List<OurPayment> findAuthenticatedBetween(Instant from, Instant to) {
        return jdbcTemplate.query(OUR_PAYMENT + "where p.authenticated_at >= ? and p.authenticated_at < ? and p.payment_key is not null",
                (row, number) -> ourPaymentOf(row), utc(from), utc(to));
    }

    @Override
    public List<OurPayment> findByPaymentKeys(Collection<String> paymentKeys) {
        List<String> keys = new ArrayList<>(paymentKeys);
        List<OurPayment> found = new ArrayList<>();
        for (int start = 0; start < keys.size(); start += KEYS_PER_QUERY) {
            List<String> chunk = keys.subList(start, Math.min(start + KEYS_PER_QUERY, keys.size()));
            found.addAll(namedParameterJdbcTemplate.query(OUR_PAYMENT + "where p.payment_key in (:keys)",
                    new MapSqlParameterSource("keys", chunk), (row, number) -> ourPaymentOf(row)));
        }
        return found;
    }

    @Override
    public void record(LocalDate day, List<Mismatch> mismatches, Instant foundAt) {
        jdbcTemplate.batchUpdate("""
                        insert into reconciliation_result
                            (business_date, payment_key, order_id, mismatch_type, our_status, our_amount, our_balance,
                             gateway_status, gateway_amount, gateway_balance, resolution, found_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) as found
                        on duplicate key update our_status = found.our_status, our_amount = found.our_amount,
                            our_balance = found.our_balance, gateway_status = found.gateway_status,
                            gateway_amount = found.gateway_amount, gateway_balance = found.gateway_balance,
                            resolution = found.resolution, found_at = found.found_at""",
                mismatches.stream().map(mismatch -> rowOf(day, mismatch, foundAt)).toList());
    }

    @Override
    public long countNeedingReview() {
        Long open = jdbcTemplate.queryForObject("select count(*) from reconciliation_result where resolution = 'NEEDS_REVIEW'", Long.class);
        return Objects.requireNonNullElse(open, 0L);
    }

    private static Object[] rowOf(LocalDate day, Mismatch mismatch, Instant foundAt) {
        return new Object[]{Date.valueOf(day), mismatch.getPaymentKey(), mismatch.getOrderId(), mismatch.getType().name(),
                nameOf(mismatch.getOurStatus()), mismatch.getOurAmount(), mismatch.getOurBalance(),
                nameOf(mismatch.getGatewayStatus()), mismatch.getGatewayAmount(), mismatch.getGatewayBalance(),
                mismatch.getResolution().name(), Timestamp.from(foundAt)};
    }

    // 인증한 때는 JPA(Hibernate)가 UTC 시각으로 남긴다. 같은 시각으로 견주려고 UTC 로 바꿔 넘긴다
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String nameOf(Enum<?> value) {
        return Optional.ofNullable(value).map(Enum::name).orElse(null);
    }

    private static OurPayment ourPaymentOf(ResultSet row) throws SQLException {
        return OurPayment.of(row.getString("payment_key"), row.getString("order_id"), row.getBigDecimal("amount"),
                PaymentStatus.valueOf(row.getString("status")), Objects.requireNonNull(row.getBigDecimal("canceled_amount")));
    }
}
