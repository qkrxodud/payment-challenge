package com.example.payment.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 주기 작업 잠금 설정(7장).
 * lease: 작업을 맡은 서버가 이만큼 맡는다. 한 주기가 도는 데 걸릴 수 있는 시간보다 길어야 한다 — 짧으면 앞 서버가 아직 도는 중에
 * 다른 서버도 맡는다. 맡은 서버가 죽으면 이 시간이 지날 때까지 다른 서버도 그 작업을 돌리지 않는다.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "payment.job-lock")
public class JobLockProperties {

    private final Duration lease;
}
