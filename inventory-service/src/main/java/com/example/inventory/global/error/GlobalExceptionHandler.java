package com.example.inventory.global.error;

import com.example.inventory.domain.error.ErrorCode;
import com.example.inventory.domain.error.ErrorType;
import com.example.inventory.domain.error.InventoryException;
import com.example.inventory.global.error.dto.ErrorResponse;
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
 * 10장: 재고 서비스의 모든 오류를 한 곳에서 {code, message} 응답으로 바꾼다(결제 서버의 처리기와 같은 규칙).
 * 업무 예외는 오류 종류로 상태 코드를 정하고, 읽지 못한 요청은 400, 웹 계층이 거절한 요청은 그 상태 코드, 그 밖은 500 이다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InventoryException.class)
    public ResponseEntity<ErrorResponse> handleInventory(InventoryException refused) {
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
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
