package com.example.payment.global.error;

import com.example.payment.global.error.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 오류를 한 곳에서 {code, message} 응답으로 바꾼다.
 * - 업무 예외: ErrorCode 의 오류 종류로 상태 코드를 정한다. 예외 문장에는 주문번호가 들어 있어 기록에는 오류 코드와 종류만 남긴다.
 * - 요청을 읽지 못함: 400. 해석 오류 문장에는 받은 글이 들어 있어 기록에는 종류만 남긴다.
 * - 웹 계층이 거절한 요청(지원하지 않는 방식 등): 그 거절의 상태 코드 그대로.
 * - 그 밖의 문제: 500, 내부 사정은 로그에만 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentChallengeException.class)
    public ResponseEntity<ErrorResponse> handlePaymentChallenge(PaymentChallengeException refused) {
        ErrorCode errorCode = refused.getErrorCode();
        log.info("Request refused with {}: {}.", errorCode, refused.getClass().getSimpleName());
        return respond(statusOf(errorCode.getType()), errorCode);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception unreadable) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(unreadable);
        log.info("Request could not be read: {}.", cause.getClass().getSimpleName());
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOthers(Exception problem) {
        if (problem instanceof org.springframework.web.ErrorResponse rejected) {
            log.info("Request rejected by the web layer with status {}.", rejected.getStatusCode().value());
            return respond(rejected.getStatusCode(), ErrorCode.INVALID_REQUEST);
        }
        log.error("Unexpected server error.", problem);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatusCode status, ErrorCode errorCode) {
        return ResponseEntity.status(status).body(ErrorResponse.of(errorCode));
    }

    // 오류 종류 → HTTP 상태. 도메인은 HTTP 를 모르므로 이 대응은 여기에만 둔다
    static HttpStatus statusOf(ErrorType type) {
        return switch (type) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UPSTREAM_FAILED -> HttpStatus.BAD_GATEWAY;
            case UPSTREAM_NO_RESPONSE -> HttpStatus.GATEWAY_TIMEOUT;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
