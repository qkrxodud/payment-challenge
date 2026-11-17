package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentPrepareRecorder;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;

/**
 * 비교 구현(7장 결정 3): MySQL 이름 잠금(GET_LOCK). 주문번호마다 이름 잠금을 잡은 뒤 찾고 없으면 남기고(3장 그대로), 커밋한 뒤에 푼다.
 * 이름 잠금은 트랜잭션이 아니라 연결(세션)에 붙고 커밋해도 풀리지 않아서, 준비 트랜잭션과 다른 연결 하나를 잠금이 풀릴 때까지 쥔다 —
 * 같은 연결에서 커밋 전에 풀면 다음 준비가 아직 보이지 않는 결제를 찾다 다시 중복 거부를 받는다. 잠금을 5초 안에 못 잡으면 내부 오류로 알린다
 */
@RequiredArgsConstructor
public class NamedLockPrepareService {

    private static final int WAIT_SECONDS = 5;

    private final DataSource dataSource;
    private final PaymentPrepareRecorder paymentPrepareRecorder;

    public PaymentResult prepare(PreparePaymentCommand command) {
        String lockName = "payment-prepare:" + command.orderId();
        try (Connection lockConnection = dataSource.getConnection()) {
            acquire(lockConnection, lockName);
            try {
                return paymentPrepareRecorder.prepare(command);
            } finally {
                release(lockConnection, lockName);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not use a named lock for a preparation.", failure);
        }
    }

    private static void acquire(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("select get_lock(?, ?)")) {
            lock.setString(1, lockName);
            lock.setInt(2, WAIT_SECONDS);
            try (ResultSet acquired = lock.executeQuery()) {
                if (acquired.next() && acquired.getInt(1) == 1) {
                    return;
                }
            }
        }
        throw new IllegalStateException("Timed out waiting for a named lock for a preparation.");
    }

    private static void release(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement unlock = connection.prepareStatement("select release_lock(?)")) {
            unlock.setString(1, lockName);
            unlock.execute();
        }
    }
}
