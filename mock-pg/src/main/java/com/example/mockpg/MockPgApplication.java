package com.example.mockpg;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 결제 서버가 승인·조회·취소를 요청하는 가짜 PG. 응답 지연과 승인 거절을 제어 API(/control)로 바꿀 수 있다.
 * 따로 띄울 때는 8090 포트를 쓴다. 결제 서버 테스트에서는 같은 JVM 안에서 임의 포트로 띄운다.
 */
@SpringBootApplication
public class MockPgApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(MockPgApplication.class)
                .properties("server.port=8090", "spring.config.name=mock-pg")
                .run(args);
    }
}
