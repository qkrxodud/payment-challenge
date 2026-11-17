package com.example.payment.domain.job;

import java.time.Instant;

/**
 * 7장: 주기 작업 잠금(작업 이름 한 줄). 서버 여러 대 가운데 한 번에 한 대만 그 작업을 맡는다.
 */
public interface JobLockRepository {

    // 아무도 맡지 않았거나 맡은 시간이 지난 작업이면 owner 가 lockedUntil 까지 맡는다. 맡았으면 맞다
    boolean tryLock(String job, String owner, Instant now, Instant lockedUntil);

    // owner 가 맡은 작업을 지금(now)까지로 줄여 다음 주기에 다른 서버도 맡을 수 있게 한다
    void unlock(String job, String owner, Instant now);
}
