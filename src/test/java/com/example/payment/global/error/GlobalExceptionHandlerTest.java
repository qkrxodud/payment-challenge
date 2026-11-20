package com.example.payment.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.global.error.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

@DisplayName("오류 응답")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // 3장에서 같은 주문번호 재준비가 정상 응답이 되면서, 결제 API 테스트로는 이 길을 지나는 요청이 없어졌다
    @Test
    @DisplayName("예상하지 못한 문제는 내부 사정을 감추고 서버 오류로 답한다")
    void unexpectedProblemIsAnsweredAsServerError() {
        ResponseEntity<ErrorResponse> answer = handler.handleOthers(new IllegalStateException("database password expired"));

        assertThat(answer.getStatusCode().value()).isEqualTo(500);
        assertThat(answer.getBody()).isEqualTo(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
