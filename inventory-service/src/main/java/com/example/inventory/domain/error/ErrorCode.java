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
    // 10장 결정 3: 이 예약 번호는 이미 해제됐다(해제가 예약보다 먼저 도착함). 재고를 잡지 않는다
    RESERVATION_RELEASED(ErrorType.CONFLICT, "This reservation was already released."),
    // 11장 결정 3: 확정도 해제도 아닌 재고 명령(Kafka 로만 받는다 — HTTP 응답으로 나가지 않는다)
    UNKNOWN_STOCK_COMMAND(ErrorType.INVALID_INPUT, "This stock command is not known."),
    // 11장 결정 3: 명령 번호·예약 번호가 없거나 명령 번호가 숫자가 아닌 재고 명령
    INVALID_STOCK_COMMAND(ErrorType.INVALID_INPUT, "This stock command could not be read."),
    // 10장: 이 번호의 예약이 없다
    RESERVATION_NOT_FOUND(ErrorType.NOT_FOUND, "No reservation was found."),
    INTERNAL_ERROR(ErrorType.INTERNAL, "Something went wrong on the server.");

    private final ErrorType type;
    private final String clientMessage;
}
