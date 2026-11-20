package com.example.payment.domain.stock;

/**
 * 10장 결정 5: 재고 명령을 메시지 브로커(Kafka)에 보낸다. 브로커가 받았다고 답할 때까지 기다린다.
 * 받았다는 답을 못 받으면 PaymentEventNotSentException 으로 알린다(9장 결제 이벤트와 같다 — 그래도 브로커에 남았을 수 있다).
 */
public interface StockCommandSender {

    void send(StockCommand command);
}
