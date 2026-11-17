package com.example.payment.global.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 결과 모름 보정에 쓰는 시계와 주기 실행(4장). 도메인은 시각을 인자로 받고, 시각은 이 시계에서 얻는다.
 * 7장부터 주기 작업 잠금 설정도 여기서 읽는다
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({ReconciliationProperties.class, JobLockProperties.class})
public class ReconciliationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
