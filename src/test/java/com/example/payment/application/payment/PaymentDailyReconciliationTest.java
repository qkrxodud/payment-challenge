package com.example.payment.application.payment;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("하루치 대사 — 운영 구현")
class PaymentDailyReconciliationTest extends DailyReconciliationContract {

    @Override
    void reconcile(LocalDate day) {
        reconcileOn(thisServer, day);
    }

    @Override
    void reconcileOn(ApplicationContext server, LocalDate day) {
        server.getBean(DailyReconciler.class).reconcile(day);
    }

    @Override
    List<ReconciliationLine> resultsOf(LocalDate day) {
        return resultsIn(jdbcTemplate, day);
    }

    // 대사 결과 표에 남은 그날의 어긋남(비교 구현도 같은 표에 남긴다)
    static List<ReconciliationLine> resultsIn(JdbcTemplate jdbcTemplate, LocalDate day) {
        return jdbcTemplate.query("select payment_key, mismatch_type, resolution from reconciliation_result where business_date = ?",
                (row, number) -> ReconciliationLine.of(row.getString("payment_key"), row.getString("mismatch_type"),
                        row.getString("resolution")), Date.valueOf(day));
    }
}
