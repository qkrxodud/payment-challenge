package com.example.payment.infrastructure.persistence.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.TestcontainersConfiguration;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, PaymentRepositoryAdapter.class})
@DisplayName("결제 기록 — 실제 보관함에서")
class PaymentRepositoryAdapterTest {

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("승인된 결제는 다시 찾아도 승인 상태다")
    void approvedPaymentStaysApproved() {
        // given: 승인까지 끝난 결제
        Payment payment = approvedPayment();

        // when
        Payment found = paymentRepository.findById(payment.getId()).orElseThrow();

        // then
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("결제 상태는 상태 이름 그대로 남는다")
    void statusIsKeptByName() {
        // given: 승인까지 끝난 결제
        Payment payment = approvedPayment();

        // when
        String stored = jdbcTemplate.queryForObject(
                "select status from payment where id = ?", String.class, payment.getId());
        String columnType = jdbcTemplate.queryForObject(
                "select column_type from information_schema.columns "
                        + "where table_schema = database() and table_name = 'payment' and column_name = 'status'",
                String.class);
        System.out.println("status column_type = " + columnType);

        // then
        assertThat(stored).isEqualTo("APPROVED");
    }

    private Payment approvedPayment() {
        Payment payment = Payment.create("order-20261114-0001", new BigDecimal("10000"));
        payment.authenticate("key-1");
        payment.approve();
        paymentRepository.save(payment);
        em.flush();
        em.clear();
        return payment;
    }
}
