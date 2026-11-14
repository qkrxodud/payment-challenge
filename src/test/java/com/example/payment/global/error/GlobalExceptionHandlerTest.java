package com.example.payment.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.global.error.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.firewall.RequestRejectedException;

@DisplayName("오류 응답")
class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("보안 검사에서 거절된 요청은 받을 수 없는 요청이라고 답한다")
    void firewallRejectedRequestIsRefused() {
        // given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        // when
        ResponseEntity<ErrorResponse> answer = handler.handleFirewallRejected(
                new RequestRejectedException("The request was rejected because the URL contained a potentially malicious String \"%2e\""));

        // then
        assertThat(answer.getStatusCode().value()).isEqualTo(400);
        assertThat(answer.getBody()).isEqualTo(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }
}
