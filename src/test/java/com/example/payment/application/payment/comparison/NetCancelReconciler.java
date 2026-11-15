package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentRecorder;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentInquiry;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 비교 구현 A: 망취소. 결과 모름 결제는 결과를 묻지 않고 카드사에 취소를 보낸 뒤 승인 실패로 닫는다.
 * 취소됐거나(2xx), 취소할 승인이 없다는 답(404 결제 없음, 400 이미 취소, 403 취소할 수 없는 결제)이면 닫는다.
 * 취소에도 답이 없거나 카드사 오류(5xx)면 그대로 두고 다음 보정에서 다시 보낸다.
 */
@RequiredArgsConstructor
public class NetCancelReconciler {

    private final PaymentRepository paymentRepository;
    private final PaymentRecorder paymentRecorder;
    private final RestClient pgRestClient;

    public void reconcile() {
        paymentRepository.findAllByStatus(PaymentStatus.APPROVAL_UNKNOWN).forEach(this::cancel);
    }

    private void cancel(Payment payment) {
        try {
            boolean closed = pgRestClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", payment.getPaymentKey())
                    .body(Map.of("cancelReason", "No answer to the approval request"))
                    .exchange((request, response) -> isAnswered(response.getStatusCode()));
            closeIf(closed, payment);
        } catch (ResourceAccessException noResponse) {
            // 다음 보정에서 다시 보낸다
        }
    }

    // 취소했다는 답이나 취소할 승인이 없다는 답. 카드사 오류(5xx)는 답으로 치지 않는다
    private static boolean isAnswered(HttpStatusCode status) {
        return status.is2xxSuccessful() || status.is4xxClientError();
    }

    private void closeIf(boolean closed, Payment payment) {
        if (closed) {
            paymentRecorder.recordInquiry(payment.getOrderId(), PaymentInquiry.NOT_APPROVED);
        }
    }
}
