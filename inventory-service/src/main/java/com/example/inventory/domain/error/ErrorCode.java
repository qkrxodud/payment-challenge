package com.example.inventory.domain.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 10장: 재고 서비스가 돌려주는 오류 코드의 단일 목록. clientMessage 는 응답에 그대로 나가는 영어 문장이다 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 요청 본문을 읽을 수 없음
    INVALID_INPUT(ErrorType.INVALID_INPUT, "The request could not be read."),
    // 웹 계층이 거절한 요청(지원하지 않는 HTTP 방식 등). 상태 코드는 그 거절의 것을 그대로 쓴다
    INVALID_REQUEST(ErrorType.INVALID_INPUT, "This request is not supported."),
    // 재고가 모자라 줄이지 않았다
    OUT_OF_STOCK(ErrorType.CONFLICT, "A product is out of stock."),
    INTERNAL_ERROR(ErrorType.INTERNAL, "Something went wrong on the server.");

    private final ErrorType type;
    private final String clientMessage;
}
