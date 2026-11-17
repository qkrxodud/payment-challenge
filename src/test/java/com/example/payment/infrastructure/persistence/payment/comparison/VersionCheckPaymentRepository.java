package com.example.payment.infrastructure.persistence.payment.comparison;

import com.example.payment.domain.payment.Payment;
import com.example.payment.global.error.exception.InvalidPaymentTransitionException;
import com.example.payment.infrastructure.persistence.payment.PaymentRepositoryAdapter;
import jakarta.annotation.PostConstruct;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 비교 구현(7장 결정 1): 변경 번호 비교(낙관적 잠금). 결제를 바꾸려고 읽을 때 잠그지 않고 변경 번호(version)를 함께 읽어 두었다가,
 * 기록을 마치기(커밋) 직전에 읽은 번호와 같을 때만 번호를 하나 올린다(update … where version = 읽은 번호).
 * 그 사이 다른 기록이 번호를 올렸으면 이 기록은 남기지 않고(롤백) 지금 상태에서는 할 수 없다(409)고 답한다. 다시 시도하지 않는다 —
 * 충돌을 그대로 드러내야 잠금 방식마다 실패 수를 잴 수 있다(측정).
 * 결제에 JPA @Version 을 두면 Hibernate 가 하는 비교를 운영 결제를 바꾸지 않고 같은 SQL 로 흉내 냈다. 결제 줄이 바뀌지 않는 기록
 * (취소 줄만 더하는 취소 요청)에서도 번호를 올린다 — JPA 의 OPTIMISTIC_FORCE_INCREMENT 와 같다. 변경 번호 칸은 이 시험 DB 에만 더한다.
 */
@Primary
public class VersionCheckPaymentRepository extends DelegatingPaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public VersionCheckPaymentRepository(PaymentRepositoryAdapter delegate, JdbcTemplate jdbcTemplate) {
        super(delegate);
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void addVersionColumn() {
        Integer columns = jdbcTemplate.queryForObject("select count(*) from information_schema.columns "
                + "where table_schema = database() and table_name = 'payment' and column_name = 'version'", Integer.class);
        if (Objects.requireNonNullElse(columns, 0) == 0) {
            jdbcTemplate.execute("alter table payment add column version bigint not null default 0");
        }
    }

    @Override
    public Optional<Payment> findByOrderIdForUpdate(String orderId) {
        Optional<Payment> found = delegate.findByOrderId(orderId);
        found.ifPresent(this::checkVersionBeforeCommit);
        return found;
    }

    private void checkVersionBeforeCommit(Payment payment) {
        Long readVersion = jdbcTemplate.queryForObject("select version from payment where id = ?", Long.class, payment.getId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                int changed = jdbcTemplate.update("update payment set version = version + 1 where id = ? and version = ?",
                        payment.getId(), readVersion);
                if (changed == 0) {
                    throw new InvalidPaymentTransitionException(payment.getStatus().name(), "CHANGE_CONCURRENTLY");
                }
            }
        });
    }
}
