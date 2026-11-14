package com.example.payment.infrastructure.persistence.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** schema.sql 대신 Hibernate 에게 스키마 생성을 맡기면 상태 컬럼이 어떤 타입이 되는지 확인한다. */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@DisplayName("저장 구조를 서버가 직접 만들게 두면")
class HibernateGeneratedSchemaTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("결제 상태 칸은 고를 수 있는 값이 미리 정해진 칸으로 만들어진다")
    void statusBecomesFixedChoiceColumn() {
        // when
        String columnType = jdbcTemplate.queryForObject(
                "select column_type from information_schema.columns "
                        + "where table_schema = database() and table_name = 'payment' and column_name = 'status'",
                String.class);
        System.out.println("hibernate status column_type = " + columnType);

        // then
        assertThat(columnType).startsWith("enum(");
    }
}
