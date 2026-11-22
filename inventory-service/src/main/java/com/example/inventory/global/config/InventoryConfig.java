package com.example.inventory.global.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 10장: 예약을 남기고 바꾼 때를 남기는 시계. 12장 결정 5부터 처리한 명령 기록의 주기 정리를 켠다 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CleanupProperties.class)
public class InventoryConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
