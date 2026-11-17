package com.example.payment.infrastructure.persistence.job;

import com.example.payment.domain.job.JobLockRepository;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 7장: job_lock 한 줄을 조건부 UPDATE 로 맡고 푼다. 맡기는 "맡은 시간이 지났을 때만 바꾼다"는 UPDATE 한 문장이라,
 * 두 서버가 같은 때 맡으려 해도 MySQL 이 같은 줄을 한 번에 하나씩 바꾸고 나중 문장은 이미 바뀐 줄을 다시 보고 0줄을 바꾼다.
 * 어느 트랜잭션에도 끼지 않고 바로 반영된다 — 작업을 도는 동안 잠금(DB 연결)을 쥐지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class JobLockRepositoryAdapter implements JobLockRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean tryLock(String job, String owner, Instant now, Instant lockedUntil) {
        int locked = jdbcTemplate.update(
                "update job_lock set locked_by = ?, locked_until = ? where name = ? and locked_until <= ?",
                owner, Timestamp.from(lockedUntil), job, Timestamp.from(now));
        return locked == 1;
    }

    @Override
    public void unlock(String job, String owner, Instant now) {
        jdbcTemplate.update("update job_lock set locked_until = ? where name = ? and locked_by = ?",
                Timestamp.from(now), job, owner);
    }
}
