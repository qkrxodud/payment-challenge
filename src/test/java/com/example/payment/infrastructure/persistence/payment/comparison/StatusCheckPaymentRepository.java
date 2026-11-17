package com.example.payment.infrastructure.persistence.payment.comparison;

import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.InvalidPaymentTransitionException;
import com.example.payment.infrastructure.persistence.payment.PaymentRepositoryAdapter;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 비교 구현(7장 결정 1): 상태 조건 UPDATE. 잠그지 않고 읽어 두었다가, 기록을 마치기(커밋) 직전에 읽은 상태 그대로일 때만 결제 상태를
 * 바꾼다(update payment set status = 새 상태 where id = ? and status = 읽은 상태). 그 사이 다른 기록이 상태를 바꿨으면 이 기록은
 * 남기지 않고(롤백) 지금 상태에서는 할 수 없다(409)고 답한다. 다시 시도하지 않는다.
 * 결제 상태가 그대로인 기록(취소 줄만 더하는 취소 요청, 결과 모름 취소 기록)은 조건이 늘 맞는다.
 */
@Primary
public class StatusCheckPaymentRepository extends DelegatingPaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public StatusCheckPaymentRepository(PaymentRepositoryAdapter delegate, JdbcTemplate jdbcTemplate) {
        super(delegate);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Payment> findByOrderIdForUpdate(String orderId) {
        Optional<Payment> found = delegate.findByOrderId(orderId);
        found.ifPresent(this::checkStatusBeforeCommit);
        return found;
    }

    private void checkStatusBeforeCommit(Payment payment) {
        PaymentStatus readStatus = payment.getStatus();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                int changed = jdbcTemplate.update("update payment set status = ? where id = ? and status = ?",
                        payment.getStatus().name(), payment.getId(), readStatus.name());
                if (changed == 0) {
                    throw new InvalidPaymentTransitionException(readStatus.name(), "CHANGE_CONCURRENTLY");
                }
            }
        });
    }
}
