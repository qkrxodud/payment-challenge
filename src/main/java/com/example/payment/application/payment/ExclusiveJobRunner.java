package com.example.payment.application.payment;

import com.example.payment.global.config.JobLockProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 주기 작업을 서버 여러 대 가운데 한 대만 돌린다(7장 결정 5). 작업마다 job_lock 한 줄을 맡은 서버만 돌고, 다른 서버는 기다리지 않고
 * 이번 주기를 넘긴다. 다 돌면(실패해도) 맡은 것을 풀어 다음 주기에 아무 서버나 맡게 한다.
 * 대상이 적어 한 대로 충분하고, 결제·취소·알림 표에 칸을 더하지 않으려고 작업 단위로 잠근다. 대신 한 작업을 서버끼리 나눠 돌릴 수 없고,
 * 맡은 서버가 죽으면 맡은 시간(payment.job-lock.lease)이 지날 때까지 그 작업이 멈춘다.
 * 같은 결제를 두 서버가 함께 바꾸는 것은 결정 1 잠금이 막는다. 이 잠금은 같은 카드사 조회·취소를 서버마다 되풀이하지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExclusiveJobRunner {

    public static final String RECONCILIATION = "payment-reconciliation";
    public static final String WEBHOOK = "payment-webhook";
    // 9장 결정 3: 남긴 결제 결과를 보내는 릴레이. 한 대가 번호 순으로 보내야 같은 결제의 이벤트 순서를 지키기 쉽다
    public static final String OUTBOX = "payment-outbox-relay";

    private final JobLockRepository jobLockRepository;
    private final JobLockProperties jobLockProperties;
    private final Clock clock;
    // 이 서버를 가리키는 이름. 서버가 뜰 때마다 새로 정한다
    private final String owner = "server-" + UUID.randomUUID();

    public void run(String job, Runnable work) {
        Instant now = clock.instant();
        if (jobLockRepository.tryLock(job, owner, now, now.plus(jobLockProperties.getLease()))) {
            runLocked(job, work);
            return;
        }
        log.debug("Another server is running {}. Skipping this round.", job);
    }

    private void runLocked(String job, Runnable work) {
        try {
            work.run();
        } finally {
            jobLockRepository.unlock(job, owner, clock.instant());
        }
    }
}
