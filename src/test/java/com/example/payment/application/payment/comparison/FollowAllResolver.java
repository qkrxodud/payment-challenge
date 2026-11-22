package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.FollowBehindResolver;
import com.example.payment.application.payment.MismatchResolver;
import com.example.payment.domain.reconciliation.Mismatch;
import com.example.payment.domain.reconciliation.MismatchType;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 12장 결정 2 비교: 모두 카드사에 맞춤. 뒤처진 결제는 운영 구현과 같이 따라가고, 나머지 어긋남도 카드사 거래 내역대로 우리 기록을 고친다 —
 * 금액은 카드사 승인 금액으로, 우리에게만 있는 승인 결제는 승인 실패로, 우리가 앞선 결제는 카드사 단계로 되돌리고, 카드사에만 있는 결제는
 * 승인 결제로 새로 남긴다. 허용 전이 표로는 할 수 없는 변경이라 결제 줄을 직접 고친다(결제 상태 이력도 남지 않는다).
 */
@Primary
@RequiredArgsConstructor
public class FollowAllResolver implements MismatchResolver {

    private final FollowBehindResolver followBehindResolver;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Mismatch resolve(Mismatch mismatch) {
        if (mismatch.isFollowable()) {
            return followBehindResolver.resolve(mismatch);
        }
        overwrite(mismatch);
        return mismatch.followed();
    }

    private void overwrite(Mismatch mismatch) {
        if (mismatch.getType() == MismatchType.AMOUNT_MISMATCH) {
            jdbcTemplate.update("update payment set amount = ? where payment_key = ?", mismatch.getGatewayAmount(),
                    mismatch.getPaymentKey());
            return;
        }
        if (mismatch.getType() == MismatchType.OURS_ONLY) {
            jdbcTemplate.update("update payment set status = 'APPROVAL_FAILED' where payment_key = ?", mismatch.getPaymentKey());
            return;
        }
        if (mismatch.getType() == MismatchType.PG_ONLY) {
            jdbcTemplate.update("insert into payment (order_id, amount, status, payment_key, authenticated_at, point_amount) "
                            + "values (?, ?, 'APPROVED', ?, now(6), 0)", mismatch.getOrderId(),
                    Objects.requireNonNullElse(mismatch.getGatewayAmount(), mismatch.getGatewayBalance()), mismatch.getPaymentKey());
            return;
        }
        jdbcTemplate.update("update payment set status = ? where payment_key = ?", statusOf(mismatch), mismatch.getPaymentKey());
    }

    private static String statusOf(Mismatch mismatch) {
        return switch (mismatch.getGatewayStatus()) {
            case CANCELED -> "APPROVAL_CANCELED";
            default -> "APPROVED";
        };
    }
}
