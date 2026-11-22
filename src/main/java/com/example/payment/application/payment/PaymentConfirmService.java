package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ConfirmAttempt;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.error.exception.InventoryNoResponseException;
import com.example.payment.domain.error.exception.OutOfStockException;
import com.example.payment.domain.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.domain.error.exception.PaymentGatewayUnavailableException;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 승인. 트랜잭션을 둘로 나누고 PG 호출은 그 사이, 트랜잭션 밖에서 한다.
 * PG 답을 기다리는 동안 DB 연결을 붙잡지 않기 위해서다.
 * 거절도 결과로 기록하고 커밋한다 — 예외로 알리면 거절 기록까지 롤백된다.
 * PG 에는 요청 금액이 아니라 준비할 때 저장한 금액을 보낸다. 둘이 다르면 인증 단계에서 이미 거부된다.
 * 같은 결제 키의 요청이 다시 오면 PG 를 다시 부르지 않고 기록해 둔 결과로 답한다.
 * PG 답을 정해진 시간 안에 받지 못하면 결과 모름으로 기록하고 응답 없음을 그대로 알린다(4장).
 * 7장부터 카드사 답을 기다리는 결제에 같은 결제 키로 다시 온 요청도 PG 를 부르지 않고 지금 상태(인증)로 답한다(결정 2).
 * 10장 결정 1부터 인증을 남긴 뒤, 카드사에 승인을 요청하기 전에 재고 서비스에 재고를 잡는다(동기 HTTP, 짧은 시간 제한).
 * 결정 4부터 재고 서비스의 답을 받지 못하면 승인을 요청하지 않고 결제를 닫은 뒤 예약 해제를 보낸다.
 * 12장 결정 4부터 카드사 장애로 차단기가 열려 승인을 요청하지 않았으면 결제를 승인 없이 닫는다(결과 모름이 아니다 — 카드사에 보내지 않았다).
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final PaymentRecorder paymentRecorder;
    private final PaymentGateway paymentGateway;
    private final PaymentHoldings paymentHoldings;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        ConfirmAttempt attempt = paymentRecorder.authenticate(command);
        if (attempt.isRepeated()) {
            return attempt.repeated();
        }
        reserveStock(attempt);
        PaymentApproval approval = requestApproval(command.paymentKey(), attempt);
        return paymentRecorder.recordApproval(command.orderId(), approval);
    }

    // 10장 결정 1: 카드사에 승인을 요청하기 전에 재고 서비스에 재고를 잡는다. 모자라면 결제를 승인 없이 닫고 재고 없음(409)으로 답한다.
    // 10장 결정 4: 답을 받지 못하면(시간 초과·연결 실패) 카드사에 승인을 요청하지 않고 결제를 승인 없이 닫은 뒤 해제를 보낸다(504).
    // 재고 서비스가 실제로 잡았든(답만 늦음), 아직 받지 못했든(요청이 늦게 도착), 아예 받지 못했든 결과가 같다 — 잡았으면 해제가 돌려주고,
    // 늦게 도착하면 먼저 남은 해제에 막히고, 받지 못했으면 해제가 빈 해제 줄만 남긴다
    private void reserveStock(ConfirmAttempt attempt) {
        boolean reserved;
        try {
            reserved = paymentHoldings.reserveStock(attempt.paymentId(), attempt.orderId(), attempt.items());
        } catch (InventoryNoResponseException noResponse) {
            paymentRecorder.recordStockNoResponse(attempt.orderId());
            throw noResponse;
        }
        if (reserved) {
            return;
        }
        paymentRecorder.recordStockRefused(attempt.orderId());
        throw new OutOfStockException(attempt.orderId());
    }

    // 답을 받지 못했어도 카드사에는 승인이 남았을 수 있다. 인증 상태로 두지 않고 결과 모름으로 남긴다
    private PaymentApproval requestApproval(String paymentKey, ConfirmAttempt attempt) {
        try {
            return paymentGateway.approve(paymentKey, attempt.orderId(), attempt.amount());
        } catch (PaymentGatewayNoResponseException noResponse) {
            paymentRecorder.recordApprovalUnknown(attempt.orderId());
            throw noResponse;
        } catch (PaymentGatewayUnavailableException unavailable) {
            paymentRecorder.recordApprovalNotRequested(attempt.orderId());
            throw unavailable;
        }
    }
}
