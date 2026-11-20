package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 7장 결정 5: 주기 작업을 서버 여러 대 가운데 한 대만 돌리는 작업 잠금. 다른 서버는 작업 잠금을 직접 맡아 흉내 낸다 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("주기 작업 잠금")
class ExclusiveJobRunnerTest {

    private static final String OTHER_SERVER = "other-server";

    @Autowired
    ExclusiveJobRunner exclusiveJobRunner;

    @Autowired
    JobLockRepository jobLockRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    String job;

    @BeforeEach
    void newJob() {
        job = "job-" + UUID.randomUUID();
        jdbcTemplate.update("insert into job_lock (name, locked_until) values (?, '1970-01-01 00:00:00')", job);
    }

    @Test
    @DisplayName("다른 서버가 맡고 있는 작업은 이 서버에서 돌리지 않고 이번 주기를 넘긴다")
    void jobTakenByOtherServerIsSkipped() {
        jobLockRepository.tryLock(job, OTHER_SERVER, Instant.now(), Instant.now().plus(Duration.ofMinutes(1)));
        AtomicInteger runs = new AtomicInteger();

        exclusiveJobRunner.run(job, runs::incrementAndGet);

        assertThat(runs).hasValue(0);
    }

    @Test
    @DisplayName("다른 서버가 맡은 시간이 지난 작업은 이 서버가 맡아 돌린다")
    void jobPastOtherServersLeaseIsRun() {
        jobLockRepository.tryLock(job, OTHER_SERVER, Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        AtomicInteger runs = new AtomicInteger();

        exclusiveJobRunner.run(job, runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("이미 맡은 작업은 맡은 시간이 지나기 전에 다른 서버가 맡지 못한다")
    void takenJobCannotBeTakenAgain() {
        jobLockRepository.tryLock(job, "first-server", Instant.now(), Instant.now().plus(Duration.ofMinutes(1)));

        boolean taken = jobLockRepository.tryLock(job, OTHER_SERVER, Instant.now(), Instant.now().plus(Duration.ofMinutes(1)));

        assertThat(taken).isFalse();
    }

    @Test
    @DisplayName("작업을 다 돌면 다음 주기에 다른 서버가 바로 맡을 수 있다")
    void finishedJobCanBeTakenByOtherServer() {
        exclusiveJobRunner.run(job, () -> { });

        boolean taken = jobLockRepository.tryLock(job, OTHER_SERVER, Instant.now(), Instant.now().plus(Duration.ofMinutes(1)));

        assertThat(taken).isTrue();
    }

    @Test
    @DisplayName("작업이 도중에 실패해도 다음 주기에 다른 서버가 바로 맡을 수 있다")
    void failedJobCanBeTakenByOtherServer() {
        catchThrowable(() -> exclusiveJobRunner.run(job, () -> {
            throw new IllegalStateException("failed for test");
        }));

        boolean taken = jobLockRepository.tryLock(job, OTHER_SERVER, Instant.now(), Instant.now().plus(Duration.ofMinutes(1)));

        assertThat(taken).isTrue();
    }
}
