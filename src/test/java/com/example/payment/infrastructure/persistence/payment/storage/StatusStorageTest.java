package com.example.payment.infrastructure.persistence.payment.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payment.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 상태 컬럼 저장 방식 비교: ORDINAL(숫자), STRING(varchar), MySQL enum 타입.
 * 배포 전 코드(V1)가 남긴 행을 enum 중간에 상태를 추가한 코드(V2)가 읽는 상황을 만든다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@DisplayName("상태를 남기는 방식 비교 — 실제 보관함에서")
class StatusStorageTest {

    @Autowired
    EntityManager em;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createTable() {
        jdbcTemplate.execute("drop table if exists status_storage");
        jdbcTemplate.execute("""
                create table status_storage (
                    id             bigint      not null primary key,
                    ordinal_status tinyint,
                    string_status  varchar(30),
                    enum_status    enum('READY','APPROVED','CAPTURED','CANCELED')
                )""");
    }

    @Test
    @DisplayName("상태 목록 중간에 새 상태를 끼우면, 순서 번호로 남긴 승인 결제가 인증만 끝난 결제로 읽힌다")
    void ordinalChangesMeaning() {
        // given: 상태를 추가하기 전 코드가 승인 결제를 남긴다
        keepApprovedPaymentWithOldCode();

        // when: 상태를 추가한 코드가 읽는다
        Integer storedOrdinal = jdbcTemplate.queryForObject(
                "select ordinal_status from status_storage where id = 1", Integer.class);
        StoredPaymentV2 readByNewCode = em.find(StoredPaymentV2.class, 1L);
        System.out.printf("stored ordinal=%d, V2 ordinal=%s, V2 string=%s%n",
                storedOrdinal, readByNewCode.getOrdinalStatus(), readByNewCode.getStringStatus());

        // then
        assertThat(readByNewCode.getOrdinalStatus()).isEqualTo(StatusV2.AUTHENTICATED);
    }

    @Test
    @DisplayName("상태 이름으로 남긴 승인 결제는 상태 목록이 바뀌어도 승인 결제로 읽힌다")
    void nameKeepsMeaning() {
        // given: 상태를 추가하기 전 코드가 승인 결제를 남긴다
        keepApprovedPaymentWithOldCode();

        // when: 상태를 추가한 코드가 읽는다
        StoredPaymentV2 readByNewCode = em.find(StoredPaymentV2.class, 1L);

        // then
        assertThat(readByNewCode.getStringStatus()).isEqualTo(StatusV2.APPROVED);
    }

    @Test
    @DisplayName("상태 이름 칸은 목록에 없던 새 상태도 받는다")
    void nameColumnAcceptsNewStatus() {
        // when
        jdbcTemplate.update("insert into status_storage (id, string_status) values (2, 'AUTHENTICATED')");

        // then
        String stored = jdbcTemplate.queryForObject(
                "select string_status from status_storage where id = 2", String.class);
        assertThat(stored).isEqualTo("AUTHENTICATED");
    }

    @Test
    @DisplayName("고를 수 있는 값이 미리 정해진 칸은 목록에 없는 새 상태를 받지 않는다")
    void fixedChoiceColumnRejectsNewStatus() {
        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into status_storage (id, enum_status) values (3, 'AUTHENTICATED')"))
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> System.out.println(
                        "enum insert error: " + ((DataAccessException) e).getMostSpecificCause().getMessage()));
    }

    private void keepApprovedPaymentWithOldCode() {
        em.persist(StoredPaymentV1.of(1L, StatusV1.APPROVED));
        em.flush();
        em.clear();
    }
}
