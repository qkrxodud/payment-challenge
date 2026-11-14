package com.example.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DisplayName("결제 서버")
class PaymentChallengeApplicationTests {

    @Test
    @DisplayName("서버를 켜면 결제 보관함에 연결된 채로 시작한다")
    void contextLoads() {
    }
}
