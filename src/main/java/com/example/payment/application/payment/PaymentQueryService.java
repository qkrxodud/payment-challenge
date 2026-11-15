package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.CancelNotFoundException;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public PaymentResult find(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentResult::from)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    // 5장: 이 결제에 그 멱등키로 온 취소. 출발 구조는 취소를 기록하지 않아 언제나 찾을 수 없다
    @Transactional(readOnly = true)
    public CancelResult findCancel(String orderId, String idempotencyKey) {
        throw new CancelNotFoundException(orderId);
    }
}
